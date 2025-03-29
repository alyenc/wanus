/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.xiushen.manus4j.utils;

import dev.xiushen.manus4j.tool.support.CodeExecutionResult;
import dev.xiushen.manus4j.tool.support.ExecuteCommandResult;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.EMPTY;

public class CodeUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(CodeUtils.class);

	public static final String WORKING_DIR = Paths.get(System.getProperty("user.dir"), "extensions").toString();

	private static final String CODE_BLOCK_PATTERN = "```(\\w*)\n(.*?)\n```";
	private static final String UNKNOWN = "unknown";
	private static final int DEFAULT_TIMEOUT = 600;

	public static List<Pair<String, String>> extractCode(String text, boolean detectSingleLineCode) {
		List<Pair<String, String>> extracted = new ArrayList<>();
		String content = contentStr(text);
		if (!detectSingleLineCode) {
			Pattern codeBlockPattern = Pattern.compile(CODE_BLOCK_PATTERN, Pattern.DOTALL);
			Matcher matcher = codeBlockPattern.matcher(content);
			while (matcher.find()) {
				String lang = matcher.group(1);
				String code = matcher.group(2);
				extracted.add(Pair.of(lang != null ? lang : "", code));
			}
			if (extracted.isEmpty()) {
				extracted.add(Pair.of(UNKNOWN, content));
			}
		}
		else {
			Pattern codePattern = Pattern.compile("\\{3\\}(\\w+)?\\s*([\\s\\S]*?){3}|([\\^]+)`");
			Matcher matcher = codePattern.matcher(content);
			while (matcher.find()) {
				String lang = matcher.group(1);
				String code = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
				extracted.add(Pair.of(lang != null ? lang.trim() : "", code.trim()));
			}
		}
		return extracted;
	}

	public static String contentStr(Object content) {
		if (content instanceof String) {
			return (String) content;
		}
		StringBuilder rst = new StringBuilder();
		List<Map<String, Object>> itemList = (List<Map<String, Object>>) content;
		for (Map<String, Object> item : itemList) {
			if (item.get("type").equals("text")) {
				rst.append(item.get("text"));
			} else {
				assert item.get("type").equals("image_url") : "Wrong content format.";
            }
		}
		return rst.toString();
	}

	public static CodeExecutionResult executeCode(String code, String lang, String filename, Boolean arm64,
												  Map<String, Object> kwargs) {
        LOGGER.info("code:{}, lang:{}, filename:{}, arm64:{}, kwargs:{}", code, lang, filename, arm64, kwargs);
		if (code == null && filename == null) {
			String error_msg = "Either code or filename must be provided.";
			LOGGER.error(error_msg);
			throw new AssertionError(error_msg);
		}

		String workDir = kwargs.containsKey("work_dir") ? (String) kwargs.get("work_dir") : null;

		if (filename == null) {
			String code_hash = md5(code);
			filename = String.format("tmp_code_%s.%s", code_hash, lang.startsWith("python") ? "py" : lang);
		}
		if (workDir == null) {
			workDir = WORKING_DIR;
		}
		String filepath = Paths.get(workDir, filename).toString();
		String file_dir = Paths.get(filepath).getParent().toString();
		try {
			Files.createDirectories(Paths.get(file_dir));
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (code != null) {
			try {
				FileWriter fout = new FileWriter(filepath);
				fout.write(code);
				fout.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		LOGGER.info("filepath:{}", filepath);

		ExecuteCommandResult executeCommandResult = null;
		if (lang.equals("python")) {
			List<String> cmds = new ArrayList<>();
			if (arm64 != null) {
				cmds.add("arch");
				cmds.add(arm64 ? "-arm64" : "-x86_64");
			}
			cmds.add("python3");
			cmds.add(filepath);
			executeCommandResult = CodeUtils.executeCommand(cmds.toArray(new String[] {}));
		} else if (lang.equals("sh")) {
			String[] cmd = { "sh", filepath, };
			executeCommandResult = CodeUtils.executeCommand(cmd);
		}

		CodeExecutionResult codeExecutionResult = new CodeExecutionResult();
		codeExecutionResult.setExitcode(executeCommandResult.getExitCode());
		codeExecutionResult.setLogs(executeCommandResult.getOutput());
		return codeExecutionResult;
	}

	public static String md5(String input) {
		return DigestUtils.md5Hex(input);
	}

	public static ExecuteCommandResult executeCommand(String... command) {
		try {
			Process process = new ProcessBuilder(command).start();

			BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			String errorResult = read(errorReader);
			LOGGER.info("read python error={}", errorResult);

			int exitCode = process.waitFor();

			if (exitCode == 0) {
				BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String successResult = read(inputReader);
				LOGGER.info("read python success={}", successResult);

				exitCode = process.waitFor();

				ExecuteCommandResult executeCommandResult = new ExecuteCommandResult();
				executeCommandResult.setExitCode(exitCode);
				executeCommandResult.setOutput(successResult);
				return executeCommandResult;
			}

			ExecuteCommandResult executeCommandResult = new ExecuteCommandResult();
			executeCommandResult.setExitCode(exitCode);
			executeCommandResult.setOutput(errorResult);
			return executeCommandResult;
		} catch (Exception e) {
			LOGGER.error("executePythonCode error", e);
			return null;
		}
	}

	private static String read(BufferedReader reader) {
		List<String> resultList = new ArrayList<>();
		String response = EMPTY;
		while (true) {
			try {
				if ((response = reader.readLine()) == null) {
					break;
				}
			} catch (IOException e) {
			}
			resultList.add(response);
		}
		return String.join("\n", resultList);
	}

}
