package com.gpt4.copilot.controller;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpt4.copilot.copilotApplication;
import com.gpt4.copilot.pojo.Conversation;
import com.gpt4.copilot.pojo.Result;
import com.gpt4.copilot.pojo.SystemSetting;
import com.unfbx.chatgpt.entity.chat.Message;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Yangyang
 * @create 2023-12-25 18:29
 */

@Slf4j
@Data
@RestController()
public class ChatController {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    /**
     * 模型
     */
    private static final String models = "{\"data\":[{\"id\":\"text-search-babbage-doc-001\",\"object\":\"model\",\"created\":1651172509,\"owned_by\":\"openai-dev\"},{\"id\":\"gpt-4\",\"object\":\"model\",\"created\":1687882411,\"owned_by\":\"openai\"},{\"id\":\"babbage\",\"object\":\"model\",\"created\":1649358449,\"owned_by\":\"openai\"},{\"id\":\"gpt-3.5-turbo-0613\",\"object\":\"model\",\"created\":1686587434,\"owned_by\":\"openai\"},{\"id\":\"text-babbage-001\",\"object\":\"model\",\"created\":1649364043,\"owned_by\":\"openai\"},{\"id\":\"gpt-3.5-turbo\",\"object\":\"model\",\"created\":1677610602,\"owned_by\":\"openai\"},{\"id\":\"gpt-3.5-turbo-1106\",\"object\":\"model\",\"created\":1698959748,\"owned_by\":\"system\"},{\"id\":\"curie-instruct-beta\",\"object\":\"model\",\"created\":1649364042,\"owned_by\":\"openai\"},{\"id\":\"gpt-3.5-turbo-0301\",\"object\":\"model\",\"created\":1677649963,\"owned_by\":\"openai\"},{\"id\":\"gpt-3.5-turbo-16k-0613\",\"object\":\"model\",\"created\":1685474247,\"owned_by\":\"openai\"},{\"id\":\"text-embedding-ada-002\",\"object\":\"model\",\"created\":1671217299,\"owned_by\":\"openai-internal\"},{\"id\":\"davinci-similarity\",\"object\":\"model\",\"created\":1651172509,\"owned_by\":\"openai-dev\"},{\"id\":\"curie-similarity\",\"object\":\"model\",\"created\":1651172510,\"owned_by\":\"openai-dev\"},{\"id\":\"babbage-search-document\",\"object\":\"model\",\"created\":1651172510,\"owned_by\":\"openai-dev\"},{\"id\":\"curie-search-document\",\"object\":\"model\",\"created\":1651172508,\"owned_by\":\"openai-dev\"},{\"id\":\"babbage-code-search-code\",\"object\":\"model\",\"created\":1651172509,\"owned_by\":\"openai-dev\"},{\"id\":\"ada-code-search-text\",\"object\":\"model\",\"created\":1651172510,\"owned_by\":\"openai-dev\"},{\"id\":\"text-search-curie-query-001\",\"object\":\"model\",\"created\":1651172509,\"owned_by\":\"openai-dev\"},{\"id\":\"text-davinci-002\",\"object\":\"model\",\"created\":1649880484,\"owned_by\":\"openai\"},{\"id\":\"ada\",\"object\":\"model\",\"created\":1649357491,\"owned_by\":\"openai\"},{\"id\":\"text-ada-001\",\"object\":\"model\",\"created\":1649364042,\"owned_by\":\"openai\"},{\"id\":\"ada-similarity\",\"object\":\"model\",\"created\":1651172507,\"owned_by\":\"openai-dev\"},{\"id\":\"code-search-ada-code-001\",\"object\":\"model\",\"created\":1651172507,\"owned_by\":\"openai-dev\"},{\"id\":\"text-similarity-ada-001\",\"object\":\"model\",\"created\":1651172505,\"owned_by\":\"openai-dev\"},{\"id\":\"text-davinci-edit-001\",\"object\":\"model\",\"created\":1649809179,\"owned_by\":\"openai\"},{\"id\":\"code-davinci-edit-001\",\"object\":\"model\",\"created\":1649880484,\"owned_by\":\"openai\"},{\"id\":\"text-search-curie-doc-001\",\"object\":\"model\",\"created\":1651172509,\"owned_by\":\"openai-dev\"},{\"id\":\"text-curie-001\",\"object\":\"model\",\"created\":1649364043,\"owned_by\":\"openai\"},{\"id\":\"curie\",\"object\":\"model\",\"created\":1649359874,\"owned_by\":\"openai\"},{\"id\":\"davinci\",\"object\":\"model\",\"created\":1649359874,\"owned_by\":\"openai\"}]}";
    /**
     * CoCopilot Token Url
     */
    private final static String get_cocopilotToken_url = "https://api.cocopilot.org/copilot_internal/v2/token";
    /**
     * Copilot Token Url
     */
    private final static String github_get_token_url = "https://api.github.com/copilot_internal/v2/token";
    /**
     * github Chat Url
     */
    private final static String github_chat_url = "https://api.githubcopilot.com/chat/completions";
    /**
     * github Embedding Url
     */
    private final static String github_embaddings = "https://api.githubcopilot.com/embeddings";
    /**
     * github header
     */
    private static final String BEARER = "Bearer ";
    /**
     * 缓存cocopilotToken
     */
    private static ConcurrentHashMap<String, String> copilotTokenList;
    /**
     * 缓存copilotToken
     */
    private static ConcurrentHashMap<String, String> coCopilotTokenList;
    /**
     * 缓存selfToken
     */
    private static ConcurrentHashMap<String, String> selfTokenList;
    /**
     * 缓存不同密钥对于的MachineId
     */
    private static ConcurrentHashMap<String, String> machineIdList;
    /**
     * 缓存cocopilotToken_limit
     */
    private static ConcurrentHashMap<String, AtomicInteger> copilotTokenLimitList;
    /**
     * 缓存copilotToken_limit
     */
    private static ConcurrentHashMap<String, AtomicInteger> coCopilotTokenLimitList;
    /**
     * 缓存selfToken_limit
     */
    private static ConcurrentHashMap<String, AtomicInteger> selfTokenLimitList;
    /**
     * 定义systemSetting
     */
    private static SystemSetting systemSetting;
    /**
     * 定义okhttp库
     */
    private static OkHttpClient client = new OkHttpClient.Builder().connectTimeout(3, TimeUnit.MINUTES)
            .readTimeout(6, TimeUnit.MINUTES)
            .writeTimeout(6, TimeUnit.MINUTES)
            .build();
    /**
     * 定义线程池里的线程名字
     */
    private static ThreadFactory threadFactory = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "chatThreadPool-" + counter.getAndIncrement());
        }
    };
    /**
     * 定义线程池
     */
    private static ExecutorService executor;

    /**
     * 初始化ChatController类
     */
    static {
        try {
            selfTokenList = new ConcurrentHashMap<>();
            copilotTokenList = new ConcurrentHashMap<>();
            coCopilotTokenList = new ConcurrentHashMap<>();
            selfTokenLimitList = new ConcurrentHashMap<>();
            copilotTokenLimitList = new ConcurrentHashMap<>();
            coCopilotTokenLimitList = new ConcurrentHashMap<>();
            machineIdList = new ConcurrentHashMap<>();
            setSystemSetting(selectSetting());
            setExecutor(systemSetting.getMaxPoolSize());
            log.info(loadData());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String loadData() {
        try {
            String projectRoot = System.getProperty("user.dir");
            String parent = projectRoot + File.separator + "machineIdList.json";
            File jsonFile = new File(parent);
            Path jsonFilePath = Paths.get(parent);
            // 如果 JSON 文件不存在，创建一个新的 JSON 对象
            if (!jsonFile.exists() || jsonFile.length() == 0) {
                try {
                    if (!jsonFile.exists()) {
                        // 创建文件machineIdList.json
                        Files.createFile(jsonFilePath);
                    }
                    // 往 machineIdList.json 文件中添加一个空对象，防止重启报错
                    Files.writeString(jsonFilePath, "{}");
                    System.out.println("空数组添加完成");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return "machineIdList.json创建完成: " + jsonFilePath;
            } else {
                String jsonStr = new String(Files.readAllBytes(Paths.get(parent)), StandardCharsets.UTF_8);
                machineIdList = com.alibaba.fastjson2.JSON.parseObject(jsonStr, new TypeReference<>() {
                });
                return machineIdList.isEmpty() ? "machineIdList is empty, initializing..."
                        : "machineIdList loaded successfully";
            }
        } catch (IOException e) {
            return "A error occur in loadData() : " + e.getMessage();
        }
    }

    public static SystemSetting getSystemSetting() {
        return systemSetting;
    }

    public static void setSystemSetting(SystemSetting systemSetting) {
        ChatController.systemSetting = systemSetting;
    }

    public static void setExecutor(Integer maxPoolSize) {
        ChatController.executor = new ThreadPoolExecutor(0, maxPoolSize, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory);
    }


    /**
     * 初始化获取环境变量
     */
    public static String selectFile() {
        String projectRoot = System.getProperty("user.dir");
        String parent = projectRoot + File.separator + "config.json";
        File jsonFile = new File(parent);
        Path jsonFilePath = Paths.get(parent);
        // 如果 JSON 文件不存在，创建一个新的 JSON 对象
        if (!jsonFile.exists()) {
            try {
                if (!jsonFile.exists()) {
                    // 创建文件machineIdList.json
                    Files.createFile(jsonFilePath);
                }
                // 往 config.json 文件中添加一个空数组，防止重启报错
                Files.writeString(jsonFilePath, "{}");
                System.out.println("空数组添加完成");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("config.json创建完成: " + jsonFilePath);
        }
        return parent;
    }

    /**
     * 获取config.json里的值
     *
     * @param jsonObject
     * @param key
     * @param defaultValue
     * @param logMessage
     * @param <T>
     * @return
     */
    private static <T> T getValueOrDefault(JSONObject jsonObject, String key, T defaultValue, String logMessage) {
        T value;
        try {
            value = (T) jsonObject.get(key);
        } catch (JSONException e) {
            value = null;
        }
        if (value == null) {
            jsonObject.put(key, defaultValue);
            log.info(logMessage);
            value = defaultValue;
        }
        return value;
    }

    /**
     * 查询config.json里的系统值
     *
     * @return systemSettings类
     */
    public static SystemSetting selectSetting() {
        String parent = selectFile();
        try {
            // 读取 JSON 文件内容
            String jsonContent = new String(Files.readAllBytes(Paths.get(parent)));
            // 将 JSON 字符串解析为 JSONObject
            JSONObject jsonObject = com.alibaba.fastjson2.JSON.parseObject(jsonContent);

            String password = getValueOrDefault(jsonObject, "password", UUID.randomUUID().toString(), "config.json没有新增password参数,现已增加！");
            if (password.length() == 0) {
                password = UUID.randomUUID().toString();
                jsonObject.put("password", password);
                log.info("config.json password未设置，现已自动帮您设置！");
            }
            Boolean gpt4Prompt = getValueOrDefault(jsonObject, "gpt4_prompt", true, "config.json没有新增gpt4_prompt参数,现已增加！");
            Integer gpt3SleepTime = getValueOrDefault(jsonObject, "gpt3_sleepTime", 0, "config.json没有新增gpt3_sleepTime参数,现已增加！");
            Integer gpt4SleepTime = getValueOrDefault(jsonObject, "gpt4_sleepTime", 100, "config.json没有新增gpt4_sleepTime参数,现已增加！");
            Integer maxPoolSize = getValueOrDefault(jsonObject, "maxPoolSize", 300, "config.json没有新增maxPoolSize参数,现已增加！");
            String vscodeVersion = getValueOrDefault(jsonObject, "vscode_version", copilotApplication.getLatestVSCodeVersion(), "config.json没有新增vscode_version参数,现已增加！");
            String copilotChatVersion = getValueOrDefault(jsonObject, "copilot_chat_version", copilotApplication.getLatestExtensionVersion("GitHub", "copilot-chat"), "config.json没有新增copilot_chat_version参数,现已增加！");
            String getTokenUrl = getValueOrDefault(jsonObject, "get_token_url", "https://api.cocopilot.org/copilot_internal/v2/token", "config.json没有新增get_token_url参数,现已增加！");
            Integer oneCopilotLimit = getValueOrDefault(jsonObject, "one_copilot_limit", 30, "config.json没有新增one_copilot_limit参数,现已增加！");
            Integer oneCoCopilotLimit = getValueOrDefault(jsonObject, "one_coCopilot_limit", 30, "config.json没有新增one_coCopilot_limit参数,现已增加！");
            Integer oneSelfCopilotLimit = getValueOrDefault(jsonObject, "one_selfCopilot_limit", 30, "config.json没有新增one_selfCopilot_limit参数,现已增加！");

            // 将修改后的 JSONObject 转换为格式化的 JSON 字符串
            String updatedJson = com.alibaba.fastjson.JSON.toJSONString(jsonObject, SerializerFeature.PrettyFormat);
            Files.write(Paths.get(parent), updatedJson.getBytes());

            // Convert JSONObject to Config class instance
            SystemSetting config = new SystemSetting();
            config.setPassword(password);
            config.setMaxPoolSize(maxPoolSize);
            config.setGpt3_sleepTime(gpt3SleepTime);
            config.setGpt4_sleepTime(gpt4SleepTime);
            config.setVscode_version(vscodeVersion);
            config.setCopilot_chat_version(copilotChatVersion);
            config.setGet_token_url(getTokenUrl);
            config.setOne_copilot_limit(oneCopilotLimit);
            config.setOne_coCopilot_limit(oneCoCopilotLimit);
            config.setOne_selfCopilot_limit(oneSelfCopilotLimit);
            config.setGpt4_prompt(gpt4Prompt);

            return config;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 为每个密钥设置一个专属的machineId
     *
     * @return hexString.toString();
     */
    private static String generateMachineId() {
        try {
            UUID uuid = UUID.randomUUID();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(uuid.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static Message getStringStringMap(String model) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        String formattedNow = now.format(format);
        String systemContent = "\\nYou are ChatGPT, a large language model trained by OpenAI.\" +\n" +
                "                \"\\nCurrent model: " + model + "\" +\n" +
                "                \"\\nCurrent time: \"" + formattedNow + "\"\\n\\n ";
        Message newMessage = Message.builder().role(Message.Role.SYSTEM).content(systemContent).build();
        return newMessage;
    }

    /**
     * 定时清空限制
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void resetLimit() {
        ExecutorService updateExecutor = Executors.newFixedThreadPool(3);
        updateExecutor.submit(() -> copilotTokenLimitList.replaceAll((k, v) -> new AtomicInteger(0)));
        updateExecutor.submit(() -> coCopilotTokenLimitList.replaceAll((k, v) -> new AtomicInteger(0)));
        updateExecutor.submit(() -> selfTokenLimitList.replaceAll((k, v) -> new AtomicInteger(0)));
        updateExecutor.shutdown();
    }

    /**
     * 修改sleep时间
     */
    @GetMapping(value = "/changeSettings")
    private Result changeSleepTime(@RequestParam("gpt3_sleepTime") Integer gpt3_sleepTime,
                                   @RequestParam("gpt4_sleepTime") Integer gpt4_sleepTime,
                                   @RequestParam("get_token_url") String get_token_url,
                                   @RequestParam("password") String password) {
        try {
            if (password.equals(selectSetting().getPassword())) {
                String parent = selectFile();
                // 读取 JSON 文件内容
                String jsonContent = new String(Files.readAllBytes(Paths.get(parent)));
                JSONObject jsonObject = com.alibaba.fastjson2.JSON.parseObject(jsonContent);
                if (gpt3_sleepTime != null && gpt4_sleepTime >= 0 && gpt4_sleepTime <= 150) {
                    systemSetting.setGpt3_sleepTime(gpt3_sleepTime);
                    jsonObject.put("gpt3_sleepTime", gpt3_sleepTime);
                }
                if (gpt4_sleepTime != null && gpt4_sleepTime >= 0 && gpt4_sleepTime <= 150) {
                    systemSetting.setGpt4_sleepTime(gpt4_sleepTime);
                    jsonObject.put("gpt4_sleepTime", gpt4_sleepTime);
                }
                if (get_token_url != null && get_token_url.startsWith("http")) {
                    systemSetting.setGet_token_url(get_token_url);
                    jsonObject.put("get_token_url", get_token_url);
                }
                // 将修改后的 JSONObject 转换为格式化的 JSON 字符串
                String updatedJson = com.alibaba.fastjson.JSON.toJSONString(jsonObject, SerializerFeature.PrettyFormat);
                Path path = Paths.get(parent);
                Files.write(path, updatedJson.getBytes());
                return Result.success("修改成功！");
            } else {
                return Result.error("管理员密码不对，请重新再试！");
            }
        } catch (Exception e) {
            return Result.error("出错了，修改失败！");
        }
    }

    /**
     * 请求体不是json
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报github copilot APIKey is wrong
     * 反代/copilot_internal/v2/token 接口
     *
     * @param request
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @PostMapping(value = "/copilot_internal/v2/token")
    public ResponseEntity<Object> getV2Token(HttpServletResponse response, HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey;
                if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                    apiKey = authorizationHeader.substring(7);
                } else {
                    return new ResponseEntity<>(Result.error("Authorization header is missing"), HttpStatus.UNAUTHORIZED);
                }
                Request request_token = new Request.Builder().url(github_get_token_url)
                        .addHeader("Host", "api.github.com")
                        .addHeader("authorization", "token " + apiKey)
                        .addHeader("Editor-Version", systemSetting.getVscode_version())
                        .addHeader("Editor-Plugin-Version", "copilot-chat/" + systemSetting.getCopilot_chat_version())
                        .addHeader("User-Agent", "GitHubCopilotChat/0.11.1")
                        .addHeader("Accept", "*/*").build();
                try (Response res = client.newCall(request_token).execute()) {
                    if (!res.isSuccessful()) {
                        return new ResponseEntity<>(Result.error("Unsuccessful response: " + res.message()), HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    response.setContentType("application/json; charset=utf-8");
                    OutputStream out = new BufferedOutputStream(response.getOutputStream());
                    InputStream in = new BufferedInputStream(res.body().byteStream());
                    // 一次拿多少数据 迭代循环
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        out.flush();
                    }
                    return new ResponseEntity<>(null, HttpStatus.OK);
                }
            } catch (IOException e) {
                throw new RuntimeException("IO Error: " + e.getMessage(), e);
            }
        }, executor);

        ResponseEntity<Object> responseEntity;

        try {
            responseEntity = future.get(6, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            future.cancel(true);
            responseEntity = new ResponseEntity<>(Result.error("The task timed out"), HttpStatus.REQUEST_TIMEOUT);
        } catch (Exception ex) {
            responseEntity = new ResponseEntity<>(Result.error("An error occurred: " + ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntity;
    }

    /**
     * 请求体不是json 会报Request body is missing or not in JSON format
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报copilot APIKey is wrong
     *
     * @param response
     * @param request
     * @param conversation
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @PostMapping(value = "/v1/chat/completions")
    public ResponseEntity<Object> coPilotConversation(HttpServletResponse response,
                                                      HttpServletRequest request,
                                                      @org.springframework.web.bind.annotation.RequestBody Conversation conversation) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        // 异步处理
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = getRequestApikey(authorizationHeader, conversation);
                if (!copilotTokenList.containsKey(apiKey)) {
                    String token = getCopilotToken(apiKey);
                    if (token == null) {
                        return new ResponseEntity<>(Result.error("Github Copilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                    }
                    copilotTokenLimitList.putIfAbsent(apiKey, new AtomicInteger(1));
                    String machineId = saveMadchineId(apiKey);
                    copilotTokenList.put(apiKey, token);
                    log.info("Github CopilotToken初始化成功！对应的机械码为：" + machineId);
                } else {
                    int requestNum = copilotTokenLimitList.get(apiKey).incrementAndGet();
                    if (requestNum > systemSetting.getOne_copilot_limit()) {
                        log.info(apiKey + " requests is " + requestNum + " rate limit exceeded");
                        return new ResponseEntity<>(Result.error("current requests is " + requestNum + " rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                    }
                }
                // 创建OkHttpClient请求 请求https://api.githubcopilot.com/chat/completions
                String chat_token = copilotTokenList.get(apiKey);
                Map<String, String> headersMap = new HashMap<>();
                //添加头部
                addHeader(headersMap, chat_token, apiKey);
                String model = modelAdjust(conversation);
                Request streamRequest = getPrompt(conversation, model, headersMap);
                try (Response resp = client.newCall(streamRequest).execute()) {
                    if (!resp.isSuccessful()) {
                        if (resp.code() == 429) {
                            return new ResponseEntity<>(Result.error("rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                        } else if (resp.code() == 400) {
                            return new ResponseEntity<>(Result.error("messages is none or too long and over 32K"), HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            String token = getCopilotToken(apiKey);
                            if (token == null) {
                                return new ResponseEntity<>(Result.error("copilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                            }
                            copilotTokenList.put(apiKey, token);
                            log.info("token过期，Github CopilotToken重置化成功！");
                            againConversation(response, conversation, token, apiKey, model);
                        }
                    } else {
                        // 流式和非流式输出
                        outPutChat(response, resp, conversation, model);
                    }
                }
            } catch (Exception e) {
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            return null;
        }, executor);

        return getObjectResponseEntity(response, future);
    }

    private String getRequestApikey(String authorizationHeader, @org.springframework.web.bind.annotation.RequestBody Conversation conversation) {
        checkConversation(conversation);
        String apiKey;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            apiKey = authorizationHeader.substring(7);
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is missing");
        }
        return apiKey;
    }

    private String getEmbRequestApikey(String authorizationHeader, @org.springframework.web.bind.annotation.RequestBody Object conversation) {
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is missing or not in JSON format");
        }
        String apiKey;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            apiKey = authorizationHeader.substring(7);
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is missing");
        }
        return apiKey;
    }

    private String modelAdjust(Conversation conversation) {
        String model = conversation.getModel();
        String temModel;
        if (model == null || !model.startsWith("gpt-4")) {
            temModel = "gpt-3.5-turbo";
        } else {
            temModel = "gpt-4";
        }
        conversation.setModel(temModel);
        return model;
    }

    /**
     * 请求体不是json 会报Request body is missing or not in JSON format
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报copilot APIKey is wrong
     *
     * @param response
     * @param request
     * @param conversation
     * @return
     * @throws JSONException
     * @throws IOException
     */

    @PostMapping(value = "/cocopilot/v1/chat/completions")
    public ResponseEntity<Object> coCoPilotConversation(HttpServletResponse response,
                                                        HttpServletRequest request,
                                                        @org.springframework.web.bind.annotation.RequestBody Conversation conversation) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        // 异步处理
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = getRequestApikey(authorizationHeader, conversation);
                if (!coCopilotTokenList.containsKey(apiKey)) {
                    String token = getCoCoToken(apiKey);
                    if (token == null) {
                        return new ResponseEntity<>(Result.error("cocopilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                    }
                    coCopilotTokenLimitList.put(apiKey, new AtomicInteger(1));
                    String machineId = saveMadchineId(apiKey);
                    coCopilotTokenList.put(apiKey, token);
                    log.info("coCopilotToken初始化成功！对应的机械码为：" + machineId);
                } else {
                    int requestNum = coCopilotTokenLimitList.get(apiKey).incrementAndGet();
                    if (requestNum > systemSetting.getOne_coCopilot_limit()) {
                        log.info(apiKey + " requests is " + requestNum + " rate limit exceeded");
                        return new ResponseEntity<>(Result.error("current requests is " + requestNum + " rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                    }
                }
                // 创建OkHttpClient请求 请求https://api.githubcopilot.com/chat/completions
                String chat_token = coCopilotTokenList.get(apiKey);
                Map<String, String> headersMap = new HashMap<>();
                //添加头部
                addHeader(headersMap, chat_token, apiKey);
                String model = modelAdjust(conversation);
                Request streamRequest = getPrompt(conversation, model, headersMap);
                try (Response resp = client.newCall(streamRequest).execute()) {
                    if (!resp.isSuccessful()) {
                        if (resp.code() == 429) {
                            return new ResponseEntity<>(Result.error("rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                        } else if (resp.code() == 400) {
                            return new ResponseEntity<>(Result.error("messages is none or too long and over 32K"), HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            String token = getCoCoToken(apiKey);
                            if (token == null) {
                                return new ResponseEntity<>(Result.error("cocopilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                            }
                            coCopilotTokenList.put(apiKey, token);
                            log.info("token过期，coCopilotToken重置化成功！");
                            againConversation(response, conversation, token, apiKey, model);
                        }
                    } else {
                        // 流式和非流式输出
                        outPutChat(response, resp, conversation, model);
                    }
                }
            } catch (Exception e) {
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            return null;
        }, executor);

        return getObjectResponseEntity(response, future);
    }

    /**
     * 返回异步responseEntity
     *
     * @param response future
     */
    private ResponseEntity<Object> getObjectResponseEntity(HttpServletResponse response, CompletableFuture<ResponseEntity<Object>> future) {
        ResponseEntity<Object> responseEntity;

        try {
            responseEntity = future.get(6, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            response.setContentType("application/json; charset=utf-8");
            future.cancel(true);
            responseEntity = new ResponseEntity<>(Result.error("The Chat timed out"), HttpStatus.REQUEST_TIMEOUT);
        } catch (Exception ex) {
            response.setContentType("application/json; charset=utf-8");
            log.error(ex.getMessage());
            responseEntity = new ResponseEntity<>(Result.error("An error occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntity;
    }

    /**
     * Message
     * 获取url和apiKey
     *
     * @param authorizationHeader
     * @param conversation
     * @throws IOException
     */
    private String[] extractApiKeyAndRequestUrl(String authorizationHeader, Conversation conversation) throws IllegalArgumentException {
        checkConversation(conversation);
        return getApiKeyAndRequestUrl(authorizationHeader);
    }

    private void checkConversation(Conversation conversation) {
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is missing or not in JSON format");
        }
        long tokens = conversation.tokens();
        if (tokens > 32 * 1024) {
            log.error("本次请求tokens is too long and over 32K: " + tokens);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message is too long and over 32K");
        } else if (tokens <= 0) {
            log.error("本次请求tokens is none: " + tokens);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message is none");
        } else {
            log.info("本次请求tokens: " + tokens);
        }
    }

    @NotNull
    private String[] getApiKeyAndRequestUrl(String authorizationHeader) {
        String apiKey = null;
        String requestUrl = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String keyAndUrl = authorizationHeader.substring(7);
            if (!keyAndUrl.contains("|")) {
                apiKey = keyAndUrl;
                ;
            } else {
                String[] parts = keyAndUrl.split("\\|");
                requestUrl = parts[0];
                apiKey = parts[1];
            }
        }
        if (apiKey == null) {
            throw new IllegalArgumentException("Authorization ApiKey is missing");
        }
        return new String[]{requestUrl, apiKey};
    }

    /**
     * embaddings
     * 获取url和apiKey
     *
     * @param authorizationHeader
     * @param conversation
     * @throws IOException
     */
    private String[] extractEmbApiKeyAndRequestUrl(String authorizationHeader, Object conversation) throws IllegalArgumentException {
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is missing or not in JSON format");
        }
        return getApiKeyAndRequestUrl(authorizationHeader);
    }

    /**
     * 请求体不是json 会报Request body is missing or not in JSON format
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报self copilot APIKey is wrong
     *
     * @param response
     * @param request
     * @param conversation
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @PostMapping(value = "/self/v1/chat/completions")
    public ResponseEntity<Object> selfConversation(HttpServletResponse response,
                                                   HttpServletRequest request,
                                                   @org.springframework.web.bind.annotation.RequestBody Conversation conversation) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        // 异步处理
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String[] result = extractApiKeyAndRequestUrl(authorizationHeader, conversation);
                String requestUrl = result[0];
                String apiKey = result[1];
                if (!selfTokenList.containsKey(apiKey)) {
                    String token = getSelfToken(apiKey, requestUrl);
                    if (token == null) {
                        return new ResponseEntity<>(Result.error("自定义self APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                    }
                    selfTokenList.put(apiKey, token);
                    String machineId = saveMadchineId(apiKey);
                    selfTokenLimitList.put(apiKey, new AtomicInteger(1));
                    log.info("自定义selfToken初始化成功！对应的机械码为：" + machineId);
                } else {
                    int requestNum = selfTokenLimitList.get(apiKey).incrementAndGet();
                    if (requestNum > systemSetting.getOne_selfCopilot_limit()) {
                        log.info(apiKey + " requests is " + requestNum + " rate limit exceeded");
                        return new ResponseEntity<>(Result.error("current requests is " + requestNum + " rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                    }
                }
                // 创建OkHttpClient请求 请求https://api.githubcopilot.com/chat/completions
                String chat_token = selfTokenList.get(apiKey);
                Map<String, String> headersMap = new HashMap<>();
                //添加头部
                addHeader(headersMap, chat_token, apiKey);
                String model = modelAdjust(conversation);
                Request streamRequest = getPrompt(conversation, model, headersMap);
                try (Response resp = client.newCall(streamRequest).execute()) {
                    log.info("response code: " + resp.body());
                    if (!resp.isSuccessful()) {
                        if (resp.code() == 429) {
                            return new ResponseEntity<>(Result.error("rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                        } else if (resp.code() == 400) {
                            return new ResponseEntity<>(Result.error("messages is none or too long and over 32K"), HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            String token = getSelfToken(apiKey, requestUrl);
                            if (token == null) {
                                return new ResponseEntity<>(Result.error("自定义self APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                            }
                            selfTokenList.put(apiKey, token);
                            log.info("token过期，自定义selfToken重置化成功！");
                            againConversation(response, conversation, token, apiKey, model);
                        }
                    } else {
                        // 流式和非流式输出
                        outPutChat(response, resp, conversation, model);
                    }
                }
            } catch (Exception e) {
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            return null;
        }, executor);

        return getObjectResponseEntity(response, future);
    }

    /**
     * 如发现token过期
     * 重新回复问题
     *
     * @param response
     * @param conversation
     * @param token
     * @return
     */
    public Object againConversation(HttpServletResponse response,
                                    @org.springframework.web.bind.annotation.RequestBody Conversation conversation,
                                    String token,
                                    String apiKey,
                                    String model) {
        try {
            Map<String, String> headersMap = new HashMap<>();
            //添加头部
            addHeader(headersMap, token, apiKey);
            Request streamRequest = getPrompt(conversation, model, headersMap);
            try (Response resp = client.newCall(streamRequest).execute()) {
                if (!resp.isSuccessful()) {
                    return new ResponseEntity<>("copilot/cocopilot/self APIKey is wrong Or your network is wrong", HttpStatus.UNAUTHORIZED);
                } else {
                    // 流式和非流式输出
                    outPutChat(response, resp, conversation, model);
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Request getPrompt(@org.springframework.web.bind.annotation.RequestBody Conversation conversation, String model, Map<String, String> headersMap) {
        try {
            if (model.startsWith("gpt-4") && systemSetting.getGpt4_prompt()) {
                Message newMessage = getStringStringMap(model);
                conversation.getMessages().add(0, newMessage);
                log.info(model + "模型，添加系统消息注入！");
            }
            String json = com.alibaba.fastjson2.JSON.toJSONString(conversation);
            RequestBody requestBody = RequestBody.create(json, JSON);
            Request.Builder requestBuilder = new Request.Builder().url(github_chat_url).post(requestBody);
            headersMap.forEach(requestBuilder::addHeader);
            Request streamRequest = requestBuilder.build();
            return streamRequest;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Request getEmdPrompt(Object conversation,
                                 Map<String, String> headersMap) {
        try {
            String json = com.alibaba.fastjson2.JSON.toJSONString(conversation);
            RequestBody requestBody = RequestBody.create(json, JSON);
            Request.Builder requestBuilder = new Request.Builder().url(github_embaddings).post(requestBody);
            headersMap.forEach(requestBuilder::addHeader);
            Request streamRequest = requestBuilder.build();
            return streamRequest;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * ghu/gho 请求
     * 请求体不是json 会报Request body is missing or not in JSON format
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报copilot APIKey is wrong
     *
     * @param response
     * @param request
     * @param conversation
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @PostMapping(value = "/v1/embeddings")
    public ResponseEntity<Object> coPilotEmbeddings(HttpServletResponse response,
                                                    HttpServletRequest request,
                                                    @org.springframework.web.bind.annotation.RequestBody Object conversation) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        // 异步处理
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = getEmbRequestApikey(authorizationHeader, conversation);
                if (!copilotTokenList.containsKey(apiKey)) {
                    String token = getCopilotToken(apiKey);
                    if (token == null) {
                        return new ResponseEntity<>(Result.error("Github Copilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                    }
                    copilotTokenLimitList.put(apiKey, new AtomicInteger(1));
                    String machineId = saveMadchineId(apiKey);
                    copilotTokenList.put(apiKey, token);
                    log.info("Github CopilotToken初始化成功！对应的机械码为：" + machineId);
                } else {
                    int requestNum = copilotTokenLimitList.get(apiKey).incrementAndGet();
                    if (requestNum > systemSetting.getOne_copilot_limit()) {
                        log.info(apiKey + " requests is " + requestNum + " rate limit exceeded");
                        return new ResponseEntity<>(Result.error("current requests is " + requestNum + " rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                    }
                }
                // 创建OkHttpClient请求 请求https://api.githubcopilot.com/chat/completions
                String chat_token = copilotTokenList.get(apiKey);
                Map<String, String> headersMap = new HashMap<>();
                //添加头部
                addHeader(headersMap, chat_token, apiKey);
                Request streamRequest = getEmdPrompt(conversation, headersMap);
                try (Response resp = client.newCall(streamRequest).execute()) {
                    if (!resp.isSuccessful()) {
                        if (resp.code() == 429) {
                            return new ResponseEntity<>(Result.error("rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                        } else if (resp.code() == 400) {
                            return new ResponseEntity<>(Result.error("Model is not accessible"), HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            String token = getCopilotToken(apiKey);
                            if (token == null) {
                                return new ResponseEntity<>(Result.error("Github Copilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                            }
                            copilotTokenList.put(apiKey, token);
                            log.info("token过期，Github CopilotToken重置化成功！");
                            againEmbeddings(response, conversation, token, apiKey);
                        }
                    } else {
                        // 非流式输出
                        outPutEmbeddings(response, resp);
                    }
                }
                return null;
            } catch (Exception e) {
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }, executor);

        return getObjectResponseEntity(future);
    }

    private ResponseEntity<Object> getObjectResponseEntity(CompletableFuture<ResponseEntity<Object>> future) {
        ResponseEntity<Object> responseEntity;

        try {
            responseEntity = future.get(6, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            future.cancel(true);
            responseEntity = new ResponseEntity<>(Result.error("The chat timed out"), HttpStatus.REQUEST_TIMEOUT);
        } catch (Exception ex) {
            log.error(ex.getMessage());
            responseEntity = new ResponseEntity<>(Result.error("An error occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntity;
    }


    /**
     * ccu 请求
     * 请求体不是json 会报Request body is missing or not in JSON format
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报copilot APIKey is wrong
     *
     * @param response
     * @param request
     * @param conversation
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @PostMapping(value = "/cocopilot/v1/embeddings")
    public ResponseEntity<Object> coCoPilotEmbeddings(HttpServletResponse response,
                                                      HttpServletRequest request,
                                                      @org.springframework.web.bind.annotation.RequestBody Object conversation) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        // 异步处理
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = getEmbRequestApikey(authorizationHeader, conversation);
                if (!coCopilotTokenList.containsKey(apiKey)) {
                    String token = getCoCoToken(apiKey);
                    if (token == null) {
                        return new ResponseEntity<>(Result.error("copilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                    }
                    coCopilotTokenLimitList.put(apiKey, new AtomicInteger(1));
                    String machineId = saveMadchineId(apiKey);
                    coCopilotTokenList.put(apiKey, token);
                    log.info("coCopilotToken初始化成功！对应的机械码为：" + machineId);
                } else {
                    int requestNum = coCopilotTokenLimitList.get(apiKey).incrementAndGet();
                    if (requestNum > systemSetting.getOne_coCopilot_limit()) {
                        log.info(apiKey + " requests is " + requestNum + " rate limit exceeded");
                        return new ResponseEntity<>(Result.error("current requests is " + requestNum + " rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                    }
                }
                // 创建OkHttpClient请求 请求https://api.githubcopilot.com/chat/completions
                String chat_token = coCopilotTokenList.get(apiKey);
                Map<String, String> headersMap = new HashMap<>();
                //添加头部
                addHeader(headersMap, chat_token, apiKey);
                Request streamRequest = getEmdPrompt(conversation, headersMap);
                try (Response resp = client.newCall(streamRequest).execute()) {
                    if (!resp.isSuccessful()) {
                        if (resp.code() == 429) {
                            return new ResponseEntity<>(Result.error("rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                        } else if (resp.code() == 400) {
                            return new ResponseEntity<>(Result.error("Model is not accessible"), HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            String token = getCoCoToken(apiKey);
                            if (token == null) {
                                return new ResponseEntity<>(Result.error("copilot APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                            }
                            coCopilotTokenList.put(apiKey, token);
                            log.info("token过期，coCopilotTokenList重置化成功！");
                            againEmbeddings(response, conversation, token, apiKey);
                        }
                    } else {
                        // 非流式输出
                        outPutEmbeddings(response, resp);
                    }
                }
                return null;
            } catch (Exception e) {
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }, executor);

        return getObjectResponseEntity(future);
    }


    /**
     * 自定义请求
     * 请求体不是json 会报Request body is missing or not in JSON format
     * Authorization token缺失  会报Authorization header is missing
     * 无法请求到chat_token 会报自定义 APIKey is wrong
     *
     * @param response
     * @param request
     * @param conversation
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @PostMapping(value = "/self/v1/embeddings")
    public ResponseEntity<Object> selfEmbeddings(HttpServletResponse response,
                                                 HttpServletRequest request,
                                                 @org.springframework.web.bind.annotation.RequestBody Object conversation) {
        String header = request.getHeader("Authorization");
        String authorizationHeader = (header != null && !header.trim().isEmpty()) ? header.trim() : null;
        // 异步处理
        CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                String[] result = extractEmbApiKeyAndRequestUrl(authorizationHeader, conversation);
                String requestUrl = result[0];
                String apiKey = result[1];
                if (!selfTokenList.containsKey(apiKey)) {
                    String token = getSelfToken(apiKey, requestUrl);
                    if (token == null) {
                        return new ResponseEntity<>(Result.error("自定义APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                    }
                    selfTokenLimitList.put(apiKey, new AtomicInteger(1));
                    String machineId = saveMadchineId(apiKey);
                    selfTokenList.put(apiKey, token);
                    log.info("自定义selfToken初始化成功！对应的机械码为：" + machineId);
                } else {
                    int requestNum = selfTokenLimitList.get(apiKey).incrementAndGet();
                    if (requestNum > systemSetting.getOne_selfCopilot_limit()) {
                        log.info(apiKey + " requests is " + requestNum + " rate limit exceeded");
                        return new ResponseEntity<>(Result.error("current requests is " + requestNum + " rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                    }
                }
                String chat_token = selfTokenList.get(apiKey);
                Map<String, String> headersMap = new HashMap<>();
                //添加头部
                addHeader(headersMap, chat_token, apiKey);
                Request streamRequest = getEmdPrompt(conversation, headersMap);
                try (Response resp = client.newCall(streamRequest).execute()) {
                    if (!resp.isSuccessful()) {
                        if (resp.code() == 429) {
                            return new ResponseEntity<>(Result.error("rate limit exceeded"), HttpStatus.TOO_MANY_REQUESTS);
                        } else if (resp.code() == 400) {
                            return new ResponseEntity<>(Result.error("Model is not accessible"), HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            String token = getSelfToken(apiKey, requestUrl);
                            if (token == null) {
                                return new ResponseEntity<>(Result.error("自定义 APIKey is wrong"), HttpStatus.UNAUTHORIZED);
                            }
                            selfTokenList.put(apiKey, token);
                            log.info("token过期，自定义selfToken重置化成功！");
                            againEmbeddings(response, conversation, token, apiKey);
                        }
                    } else {
                        // 非流式输出
                        outPutEmbeddings(response, resp);
                    }
                }
                return null;
            } catch (Exception e) {
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }, executor);
        return getObjectResponseEntity(future);
    }

    public Object againEmbeddings(HttpServletResponse response,
                                  @org.springframework.web.bind.annotation.RequestBody Object conversation,
                                  String token,
                                  String apiKey) {
        try {
            Map<String, String> headersMap = new HashMap<>();
            //添加头部
            addHeader(headersMap, token, apiKey);
            String json = com.alibaba.fastjson2.JSON.toJSONString(conversation);
            RequestBody requestBody = RequestBody.create(json, JSON);
            Request.Builder requestBuilder = new Request.Builder().url(github_embaddings).post(requestBody);
            headersMap.forEach(requestBuilder::addHeader);
            Request streamRequest = requestBuilder.build();
            try (Response resp = client.newCall(streamRequest).execute()) {
                if (!resp.isSuccessful()) {
                    return new ResponseEntity<>(Result.error("copilot/cocopilot/自定义 APIKey is wrong Or your network is wrong"), HttpStatus.UNAUTHORIZED);
                } else {
                    // 非流式输出
                    outPutEmbeddings(response, resp);
                }
            }
            return null;
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 用于copilot——ghu或gho 拿到token
     *
     * @param apiKey
     * @return
     * @throws IOException
     */
    private String getCopilotToken(String apiKey) throws IOException {
        Request request = new Request.Builder().url(github_get_token_url)
                .addHeader("Host", "api.github.com")
                .addHeader("authorization", "token " + apiKey)
                .addHeader("Editor-Version", systemSetting.getVscode_version())
                .addHeader("Editor-Plugin-Version", "copilot-chat/" + systemSetting.getCopilot_chat_version())
                .addHeader("User-Agent", "GitHubCopilotChat/" + systemSetting.getCopilot_chat_version())
                .addHeader("Accept", "*/*").build();
        return getToken(request);
    }

    @Nullable
    private String getToken(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            log.info(response.toString());
            if (!response.isSuccessful()) {
                return null;
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = com.alibaba.fastjson2.JSON.parseObject(responseBody);
            return jsonResponse.getString("token");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 用于cocopilot——ccu 拿到token
     *
     * @param apiKey
     * @return
     * @throws IOException
     */
    private String getCoCoToken(String apiKey) throws IOException {
        Request request = new Request.Builder().url(get_cocopilotToken_url)
                .addHeader("authorization", "token " + apiKey)
                .addHeader("Editor-Version", systemSetting.getVscode_version())
                .addHeader("Editor-Plugin-Version", "copilot-chat/" + systemSetting.getCopilot_chat_version())
                .addHeader("User-Agent", "GitHubCopilotChat/" + systemSetting.getCopilot_chat_version())
                .addHeader("Accept", "*/*").build();
        return getToken(request);
    }

    /**
     * 用于self——ccu 拿到token
     *
     * @param apiKey
     * @return
     * @throws IOException
     */
    private String getSelfToken(String apiKey, String temRequestUrl) throws IOException {
        String requestUrl = Optional.ofNullable(temRequestUrl).orElse(systemSetting.getGet_token_url());
        log.info("请求token地址: " + requestUrl + " apiKey: " + apiKey);
        Request request = new Request.Builder().url(requestUrl)
                .addHeader("authorization", "token " + apiKey)
                .addHeader("Editor-Version", systemSetting.getVscode_version())
                .addHeader("Editor-Plugin-Version", "copilot-chat/" + systemSetting.getCopilot_chat_version())
                .addHeader("User-Agent", "GitHubCopilotChat/" + systemSetting.getCopilot_chat_version())
                .addHeader("Accept", "*/*").build();
        return getToken(request);
    }

    /**
     * 获取模型
     *
     * @return
     */
    private Object getModels() {
        try {
            Future<Object> future = executor.submit(() -> {
                String jsonString = models;
                return new ObjectMapper().readTree(jsonString);
            });
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * copilot的模型
     *
     * @return
     * @throws JsonProcessingException
     */
    @GetMapping("*/v1/models")
    public Object models() {
        return getModels();
    }

    /**
     * 提问请求头
     *
     * @param headersMap
     * @param chat_token
     */
    private void addHeader(Map<String, String> headersMap, String chat_token, String apiKey) {
        headersMap.put("Host", "api.githubcopilot.com");
        headersMap.put("Accept-Encoding", "gzip, deflate, br");
        headersMap.put("Accept", "*/*");
        headersMap.put("Authorization", "Bearer " + chat_token);
        headersMap.put("X-Request-Id", UUID.randomUUID().toString());
        headersMap.put("X-Github-Api-Version", "2023-07-07");
        headersMap.put("Vscode-Sessionid", UUID.randomUUID().toString() + System.currentTimeMillis());
        headersMap.put("vscode-machineid", machineIdList.get(apiKey));
        headersMap.put("Editor-Version", systemSetting.getVscode_version());
        headersMap.put("Editor-Plugin-Version", "copilot-chat/" + systemSetting.getCopilot_chat_version());
        headersMap.put("Openai-Organization", "github-copilot");
        headersMap.put("Copilot-Integration-Id", "vscode-chat");
        headersMap.put("Openai-Intent", "conversation-panel");
        headersMap.put("User-Agent", "GitHubCopilotChat/" + systemSetting.getCopilot_chat_version());
    }

    /**
     * chat接口的输出
     *
     * @param response
     * @param resp
     * @param conversation
     */
    private void outPutChat(HttpServletResponse response, Response resp, Conversation conversation, String model) {
        try {
            boolean isStream = conversation.isStream();
            int sleep_time = calculateSleepTime(model, isStream);
            if (isStream) {
                response.setContentType("text/event-stream; charset=UTF-8");
            } else {
                response.setContentType("application/json; charset=utf-8");
            }

            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = in.readLine()) != null) {
                    out.println(line);
                    out.flush();
                    if (sleep_time > 0 && line.startsWith("data")) {
                        Thread.sleep(sleep_time);
                    }
                }
                log.info("使用模型：" + model + "，vscode_version：" + systemSetting.getVscode_version() +
                        "，copilot_chat_version：" + systemSetting.getCopilot_chat_version()
                        + "，字符间隔时间：" + sleep_time + "ms，响应：" + resp);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Thread was interrupted", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("IO Exception occurred", e);
        }
    }

    /**
     * chat接口的每个字的睡眠时间
     */
    private int calculateSleepTime(String model, boolean isStream) {
        if (isStream) {
            if (!model.contains("gpt-4")) {
                return systemSetting.getGpt3_sleepTime();
            } else {
                return systemSetting.getGpt4_sleepTime();
            }
        } else {
            return 0;
        }
    }

    /**
     * Embeddings接口的输出
     *
     * @param response
     * @param resp
     */
    private void outPutEmbeddings(HttpServletResponse response, Response resp) {
        try {
            response.setContentType("application/json; charset=utf-8");
            OutputStream out = new BufferedOutputStream(response.getOutputStream());
            InputStream in = new BufferedInputStream(resp.body().byteStream());
            // 一次拿多少数据 迭代循环
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 按照请求生成机械码并保存
     */
    private String saveMadchineId(String apiKey) {
        try {
            if (machineIdList.containsKey(apiKey)) {
                String machineId = machineIdList.get(apiKey);
                log.info("机械码读取成功！对应的机械码为：" + machineId);
                return machineId;
            }
            String machineId = generateMachineId();
            log.info("机械码生成成功！对应的机械码为：" + machineId);
            machineIdList.put(apiKey, machineId);
            String machineIds = JSONObject.toJSONString(machineIdList);
            String projectRoot = System.getProperty("user.dir");
            String parent = projectRoot + File.separator + "machineIdList.json";
            try (FileWriter fileWriter = new FileWriter(parent)) {
                fileWriter.write(machineIds);
                log.info("保存机械码到machineIdList.json文件成功！");
            } catch (IOException e) {
                log.info("保存机械码到machineIdList.json文件失败！");
                e.printStackTrace();
            }
            return machineId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}