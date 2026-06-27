package com.ets100.reader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JsonParser {

    /**
     * 解析 content.json，根据 structure_type 分发处理
     */
    public static List<QuestionResult> parse(String jsonContent) throws JSONException {
        JSONObject root = new JSONObject(jsonContent);
        String structureType = root.optString("structure_type", "");

        if ("collector.read".equals(structureType)) {
            return parseRead(root);
        } else if ("collector.choose".equals(structureType)) {
            return parseChoose(root);
        } else if ("collector.role".equals(structureType)) {
            return parseRole(root);
        } else if ("collector.picture".equals(structureType)) {
            return parsePicture(root);
        } else {
            return parseGeneric(root);
        }
    }

    /**
     * collector.read: 阅读材料
     * info.value 可能是 String 或 JSONArray
     * 如果内容含中文，优先用 ai 或 symbol 字段（纯英文）
     */
    private static List<QuestionResult> parseRead(JSONObject root) throws JSONException {
        List<QuestionResult> results = new ArrayList<>();
        JSONObject info = root.optJSONObject("info");
        if (info == null) return results;

        Object valueObj = info.opt("value");
        if (valueObj == null) {
            results.add(QuestionResult.fromNoAnswer(1));
            return results;
        }

        String value = null;

        if (valueObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) valueObj;
            for (int i = 0; i < arr.length(); i++) {
                String item = arr.optString(i, "");
                if (!item.isEmpty() && !containsChinese(item)) {
                    value = item;
                    break;
                }
            }
            if (value == null) {
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.optString(i, "");
                    if (!item.isEmpty()) {
                        value = item;
                        break;
                    }
                }
            }
        } else {
            value = valueObj.toString();
        }

        if (value != null && containsChinese(value)) {
            String alt = info.optString("ai", "");
            if (!alt.isEmpty() && !containsChinese(alt)) {
                value = alt;
            } else {
                alt = info.optString("symbol", "");
                if (!alt.isEmpty() && !containsChinese(alt)) {
                    value = alt;
                }
            }
        }

        if (value != null && !value.isEmpty()) {
            value = stripHtml(value);
            results.add(QuestionResult.fromRead(1, value));
        } else {
            results.add(QuestionResult.fromNoAnswer(1));
        }
        return results;
    }

    /**
     * collector.choose: 选择题
     * 输出 xtlist 里每道题的 answer 字母
     *
     * 【重要】阅读材料（st_nr）放在题目上方，作为上下文参考。
     * 这是用户明确要求的展示顺序，不要改动。
     */
    private static List<QuestionResult> parseChoose(JSONObject root) throws JSONException {
        List<QuestionResult> results = new ArrayList<>();
        JSONObject info = root.optJSONObject("info");
        if (info == null) return results;

        // 阅读材料放在题目上方（用户要求的固定顺序，不要改）
        String passage = info.optString("st_nr", "");
        if (!passage.isEmpty()) {
            passage = stripHtml(passage);
            results.add(QuestionResult.fromRead(0, passage));
        }

        JSONArray xtlist = info.optJSONArray("xtlist");
        if (xtlist == null) return results;

        for (int i = 0; i < xtlist.length(); i++) {
            JSONObject question = xtlist.getJSONObject(i);
            int num = i + 1;
            String questionText = question.optString("xt_nr", "");
            String answer = question.optString("answer", "");

            questionText = stripHtml(questionText);

            if (!answer.isEmpty()) {
                results.add(QuestionResult.fromChoose(num, answer, questionText));
            } else {
                results.add(QuestionResult.fromNoAnswer(num));
            }
        }
        return results;
    }

    /**
     * collector.role: 角色扮演题
     * 输出 std 数组的第1个和倒数第2个 value
     * info.value 是中文场景描述，放在题目上方作为上下文
     */
    private static List<QuestionResult> parseRole(JSONObject root) throws JSONException {
        List<QuestionResult> results = new ArrayList<>();
        JSONObject info = root.optJSONObject("info");
        if (info == null) return results;

        String sceneDesc = info.optString("value", "");
        if (!sceneDesc.isEmpty()) {
            sceneDesc = stripHtml(sceneDesc);
            results.add(QuestionResult.fromRead(0, "【场景】" + sceneDesc));
        }

        JSONArray questions = info.optJSONArray("question");
        if (questions == null) return results;

        for (int i = 0; i < questions.length(); i++) {
            JSONObject question = questions.getJSONObject(i);
            int num = i + 1;
            String questionText = question.optString("ask", "");
            questionText = stripHtml(questionText);

            JSONArray stdArray = question.optJSONArray("std");
            if (stdArray == null || stdArray.length() == 0) {
                results.add(QuestionResult.fromNoAnswer(num));
                continue;
            }

            String firstValue = stdArray.getJSONObject(0).optString("value", "");

            String secondLastValue;
            if (stdArray.length() >= 2) {
                secondLastValue = stdArray.getJSONObject(stdArray.length() - 2).optString("value", "");
            } else {
                secondLastValue = firstValue;
            }

            if (!firstValue.isEmpty() || !secondLastValue.isEmpty()) {
                results.add(QuestionResult.fromRole(num, firstValue, secondLastValue, questionText));
            } else {
                results.add(QuestionResult.fromNoAnswer(num));
            }
        }
        return results;
    }

    /**
     * collector.picture: 看图说话/话题讨论
     * info.std 数组里是阅读段落（value 字段）
     * info.value 是中文话题标题，info.topic 是英文话题
     * 输出 std 里每个段落的英文内容
     */
    private static List<QuestionResult> parsePicture(JSONObject root) throws JSONException {
        List<QuestionResult> results = new ArrayList<>();
        JSONObject info = root.optJSONObject("info");
        if (info == null) return results;

        // 先输出话题标题（用英文 topic，跳过中文 info.value）
        String topic = info.optString("topic", "");
        if (topic.isEmpty() || containsChinese(topic)) {
            topic = info.optString("value", "");
        }
        if (!topic.isEmpty() && !containsChinese(topic)) {
            results.add(QuestionResult.fromRead(0, "【话题】" + stripHtml(topic)));
        }

        // 输出 std 数组里的段落
        JSONArray stdArray = info.optJSONArray("std");
        if (stdArray == null) return results;

        for (int i = 0; i < stdArray.length(); i++) {
            JSONObject item = stdArray.getJSONObject(i);
            String value = item.optString("value", "");
            if (value.isEmpty()) {
                value = item.optString("ai", "");
            }
            if (!value.isEmpty()) {
                value = stripHtml(value);
                results.add(QuestionResult.fromRead(i + 1, value));
            }
        }
        return results;
    }

    /**
     * 通用解析：兜底
     */
    private static List<QuestionResult> parseGeneric(JSONObject root) throws JSONException {
        List<QuestionResult> results = new ArrayList<>();

        if (root.has("answer")) {
            String answer = root.optString("answer", "");
            if (!answer.isEmpty()) {
                results.add(QuestionResult.fromChoose(1, answer, ""));
                return results;
            }
        }

        if (root.has("value")) {
            String value = root.optString("value", "");
            if (!value.isEmpty()) {
                value = stripHtml(value);
                results.add(QuestionResult.fromRead(1, value));
                return results;
            }
        }

        JSONObject info = root.optJSONObject("info");
        if (info != null && info.has("value")) {
            String value = info.optString("value", "");
            if (!value.isEmpty()) {
                value = stripHtml(value);
                results.add(QuestionResult.fromRead(1, value));
                return results;
            }
        }

        results.add(QuestionResult.fromNoAnswer(1));
        return results;
    }

    /**
     * 检测字符串是否包含中文字符
     */
    private static boolean containsChinese(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    /**
     * 清理 HTML 标签和实体
     */
    private static String stripHtml(String html) {
        if (html == null) return "";
        return html
                .replaceAll("</?p>", "")
                .replaceAll("</?br>", "\n")
                .replaceAll("</br>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .trim();
    }
}
