package com.ets100.reader;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shizuku UserService — 在 ADB/root 身份的独立进程中执行 shell 命令
 */
public class MyUserService extends IUserService.Stub {

    @Override
    public String exec(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
