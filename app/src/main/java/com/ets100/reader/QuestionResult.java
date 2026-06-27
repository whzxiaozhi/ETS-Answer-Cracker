package com.ets100.reader;

import androidx.annotation.Nullable;

/**
 * 题目解析结果数据类
 */
public class QuestionResult {

    private final int questionNumber;
    private final String questionType;
    private final String content;
    private final String answer;
    private final String questionText;
    private final String firstValue;
    private final String secondLastValue;
    private int groupId;
    private String sourceFile;

    private QuestionResult(int questionNumber, String questionType,
                           String content, String answer, String questionText,
                           String firstValue, String secondLastValue) {
        this.questionNumber = questionNumber;
        this.questionType = questionType;
        this.content = content;
        this.answer = answer;
        this.questionText = questionText;
        this.firstValue = firstValue;
        this.secondLastValue = secondLastValue;
    }

    public void setGroup(int groupId, String sourceFile) {
        this.groupId = groupId;
        this.sourceFile = sourceFile;
    }

    public int getGroupId() { return groupId; }
    public String getSourceFile() { return sourceFile; }

    public static QuestionResult fromRead(int num, String content) {
        return new QuestionResult(num, "read", content, null, null, null, null);
    }

    public static QuestionResult fromChoose(int num, String answer, String questionText) {
        return new QuestionResult(num, "choose", null, answer, questionText, null, null);
    }

    public static QuestionResult fromRole(int num, String first, String secondLast, String questionText) {
        return new QuestionResult(num, "role", null, null, questionText, first, secondLast);
    }

    public static QuestionResult fromNoAnswer(int num) {
        return new QuestionResult(num, "no_answer", null, null, null, null, null);
    }

    public static QuestionResult fromError(String errorMsg) {
        return new QuestionResult(0, "error", errorMsg, null, null, null, null);
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    @Nullable
    public String getQuestionType() {
        return questionType;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    @Nullable
    public String getAnswer() {
        return answer;
    }

    @Nullable
    public String getQuestionText() {
        return questionText;
    }

    @Nullable
    public String getFirstValue() {
        return firstValue;
    }

    @Nullable
    public String getSecondLastValue() {
        return secondLastValue;
    }
}
