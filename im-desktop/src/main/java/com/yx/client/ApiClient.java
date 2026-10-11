package com.yx.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yx.util.TokenStorage;
import lombok.Setter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 封装所有 HTTP 接口调用。
 * 支持 401 自动刷新 Token。
 */
public class ApiClient {

    private final String server;
    private final int httpPort;

    @Setter
    private String accessToken;
    private final Object refreshLock = new Object();
    private volatile long lastRefreshTime = 0;
    private static final long REFRESH_COOLDOWN_MS = 3000;

    public ApiClient(String server, int httpPort) {
        this.server = server;
        this.httpPort = httpPort;
    }

    private String baseUrl() {
        return "http://" + server + ":" + httpPort;
    }

    // ==================== 通用请求（含 401 自动刷新） ====================

    /** GET + form 参数，401 自动刷新后重试 */
    private String doGet(String path, Map<String, Object> params) {
        String resp = sendGet(path, params, accessToken);
        if (isUnauthorized(resp)) {
            String newAccess = refreshToken();
            if (newAccess == null) {
                if (TokenStorage.load() == null) {
                    TokenStorage.clear();   // 幂等，清也无所谓
                }
                this.accessToken = null;
                throw new RuntimeException("登录已过期，请重新登录");
            }
            resp = sendGet(path, params, newAccess);
        }
        return resp;
    }

    /** POST + JSON body，401 自动刷新后重试 */
    private String doPostJson(String path, String jsonBody) {
        String resp = sendPostJson(path, jsonBody, accessToken);
        if (isUnauthorized(resp)) {
            String newAccess = refreshToken();
            if (newAccess == null) {
                TokenStorage.clear();
                this.accessToken = null;
                throw new RuntimeException("登录已过期，请重新登录");
            }
            resp = sendPostJson(path, jsonBody, newAccess);
        }
        return resp;
    }

    /** POST + form 参数，401 自动刷新后重试 */
    private String doPostForm(String path, Map<String, Object> params) {
        String resp = sendPostForm(path, params, accessToken);
        if (isUnauthorized(resp)) {
            String newAccess = refreshToken();
            if (newAccess == null) {
                TokenStorage.clear();
                this.accessToken = null;
                throw new RuntimeException("登录已过期，请重新登录");
            }
            resp = sendPostForm(path, params, newAccess);
        }
        return resp;
    }

    // ---------- 底层发送 ----------

    private String sendGet(String path, Map<String, Object> params, String token) {
        HttpRequest req = HttpRequest.get(baseUrl() + path);
        if (params != null) {
            params.forEach(req::form);
        }
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return req.execute().body();
    }

    private String sendPostJson(String path, String jsonBody, String token) {
        HttpRequest req = HttpRequest.post(baseUrl() + path)
                .body(jsonBody)
                .header("Content-Type", "application/json");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return req.execute().body();
    }

    private String sendPostForm(String path, Map<String, Object> params, String token) {
        HttpRequest req = HttpRequest.post(baseUrl() + path);
        if (params != null) {
            params.forEach(req::form);
        }
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return req.execute().body();
    }

    /** 判断响应是不是 code=401 */
    private boolean isUnauthorized(String resp) {
        if (resp == null || resp.isEmpty()) return false;
        try {
            JSONObject json = JSONUtil.parseObj(resp);
            return json.getInt("code", 0) == 401;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 1. 登录 ====================

    public String login(String username, String password) {
        String resp = HttpRequest.post(baseUrl() + "/login")
                .form("username", username)
                .form("password", password)
                .execute()
                .body();

        JSONObject json = JSONUtil.parseObj(resp);
        if (json.getInt("code") != 200) {
            return null;
        }

        JSONObject data = json.getJSONObject("data");
        String access = data.getStr("accessToken");
        String refresh = data.getStr("refreshToken");

        if (refresh != null) {
            TokenStorage.save(refresh);
        }

        return access;
    }

    // ==================== 2. 注册 ====================

    public boolean registerUser(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        // ★ 直接用裸 HttpRequest，不带 Authorization
        String resp = HttpRequest.post(baseUrl() + "/register/user")
                .body(JSONUtil.toJsonStr(body))
                .header("Content-Type", "application/json")
                .execute()
                .body();
        return JSONUtil.parseObj(resp).getInt("code") == 200;
    }

    // ==================== 3. 修改个人信息 ====================

    public boolean updatePersonalInfo(String nickname, String password) {
        Map<String, Object> body = new HashMap<>();
        if (nickname != null) body.put("nickname", nickname);
        if (password != null) body.put("password", password);
        String resp = doPostJson("/user/personalInfo", JSONUtil.toJsonStr(body));
        return JSONUtil.parseObj(resp).getInt("code") == 200;
    }

    // ==================== 4. 注销账户 ====================

    public boolean revoke() {
        try {
            String resp = doPostForm("/auth/revoke", null);
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==================== 5. 退出登录 ====================

    public void logout() {
        try {
            HttpRequest.post(baseUrl() + "/auth/logout")
                    .header("Authorization", "Bearer " + accessToken)
                    .execute();
        } finally {
            TokenStorage.clear();
            this.accessToken = null;
        }
    }

    // ==================== 6. 刷新 Token ====================

    // ==================== 6. 刷新 Token ====================

    /**
     * 用本地 Refresh Token 换新 Access Token。
     * 加锁 + 冷却时间，防止并发刷新导致旧 Token 失效。
     */
    public String refreshToken() {
        synchronized (refreshLock) {
            // 冷却期内且已有新 Token，直接复用
            long now = System.currentTimeMillis();
            if (now - lastRefreshTime < REFRESH_COOLDOWN_MS && accessToken != null) {
                System.out.println("[ApiClient] 刚刷新过，复用新 Token");
                return accessToken;
            }

            String refresh = TokenStorage.load();
            if (refresh == null) {
                System.out.println("[ApiClient] 本地无 Refresh Token");
                return null;
            }

            try {
                String resp = HttpRequest.post(baseUrl() + "/auth/refresh")
                        .header("Authorization", "Bearer " + refresh)
                        .execute()
                        .body();
                System.out.println("[ApiClient] 用的 Refresh Token: " + refresh);
                System.out.println("[ApiClient] refresh 响应: " + resp);

                JSONObject json = JSONUtil.parseObj(resp);
                if (json.getInt("code") != 200) {
                    return null;
                }

                JSONObject data = json.getJSONObject("data");
                String newAccess = data.getStr("accessToken");
                String newRefresh = data.getStr("refreshToken");

                this.accessToken = newAccess;
                if (newRefresh != null) {
                    TokenStorage.save(newRefresh);
                }

                lastRefreshTime = System.currentTimeMillis();
                return newAccess;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
    // ==================== 7. 管理员 ====================

    public boolean changeStatus(String username, int status) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        params.put("status", String.valueOf(status));
        String resp = doPostForm("/changeStatus", params);
        return JSONUtil.parseObj(resp).getInt("code") == 200;
    }

    // ==================== 8. 文件上传 ====================

    public String uploadFile(File file) {
        try {
            String resp = HttpRequest.post(baseUrl() + "/file/upload")
                    .form("file", file)
                    .header("Authorization", "Bearer " + accessToken)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("code") == 200) {
                return json.getStr("data");
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== 9. 用户搜索 / 关注 ====================

    public JSONObject searchUser(String userName) {
        try {
            String resp = doGet("/searchUser", Map.of("userName", userName));
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("code") == 200) {
                return json.getJSONObject("data");
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean follow(String toUserName) {
        try {
            String resp = doPostForm("/follow", Map.of("toUserName", toUserName));
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean cancelFollow(String toUserName) {
        try {
            String resp = doPostForm("/cancelFollow", Map.of("toUserName", toUserName));
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<JSONObject> getMyFollow() {
        return queryList("/getMyFollow", null);
    }

    public List<JSONObject> getFollowMy() {
        return queryList("/getFollowMy", null);
    }

    public List<JSONObject> getFriends() {
        return queryList("/getFriends", null);
    }

    // ==================== 10. 消息 ====================

    public boolean readMessage(String fromUserName) {
        try {
            String resp = doPostForm("/readMessage",
                    Map.of("fromUserName", fromUserName));
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<JSONObject> getHistory(String fromUserName, int size, Long lastMsgId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fromUserName", fromUserName);
        params.put("size", size);
        if (lastMsgId != null) {
            params.put("lastMsgId", lastMsgId);
        }
        return queryList("/getHistory", params);
    }

    public List<JSONObject> getRecent(String fromUserName) {
        return queryList("/getRecent", Map.of("fromUserName", fromUserName));
    }

    // ==================== 11. 群相关 ====================

    public boolean createGroup(String groupName) {
        try {
            String resp = doPostForm("/createGroup",
                    Map.of("groupName", groupName));
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 邀请进群
     * @return null 失败 / true 需审批 / false 直接入群
     */
    public Boolean inviteIntoGroup(Long groupId, String username) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", groupId);
            params.put("username", username);
            String resp = doPostForm("/inviteIntoGroup", params);
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("code") != 200) return null;
            JSONObject data = json.getJSONObject("data");
            if (data == null) return null;
            return data.getBool("needApproval", true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean leaveGroup(Long groupId, String username) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("groupId", groupId);
            params.put("username", username);
            String resp = doPostForm("/leaveGroup", params);
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean stopChat(Long groupId) {
        try {
            String resp = doPostForm("/stopChat", Map.of("groupId", groupId));
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean cancelStopChat(Long groupId) {
        try {
            String resp = doPostForm("/cancelStopChat", Map.of("groupId", groupId));
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setMemberRole(String toUsername, Long groupId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("toUsername", toUsername);
            params.put("groupId", groupId);
            String resp = doPostForm("/setMemberRole", params);
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean agreeOrReject(boolean isAgree, Long groupId, String inviteeName) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("isAgree", isAgree);
            params.put("groupId", groupId);
            params.put("inviteeName", inviteeName);
            String resp = doPostForm("/agreeOrReject", params);
            return JSONUtil.parseObj(resp).getInt("code") == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<JSONObject> getMyGroups() {
        return queryList("/getMyGroups", null);
    }

    public List<JSONObject> getGroupRecent(Long groupId) {
        return queryList("/getGroupRecent", Map.of("groupId", groupId));
    }

    public List<JSONObject> getGroupHistory(Long groupId, int size, Long lastMsgId) {
        Map<String, Object> params = new HashMap<>();
        params.put("groupId", groupId);
        params.put("size", size);
        if (lastMsgId != null) {
            params.put("lastMsgId", lastMsgId);
        }
        return queryList("/getGroupHistory", params);
    }

    public List<JSONObject> getGroupMembers(Long groupId) {
        return queryList("/getGroupMembers", Map.of("groupId", groupId));
    }

    public List<String> getUnreadSenders() {
        try {
            String resp = doGet("/getUnreadSenders", null);
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("code") == 200) {
                JSONArray arr = json.getJSONArray("data");
                List<String> list = new ArrayList<>();
                if (arr != null) {
                    for (Object o : arr) list.add(String.valueOf(o));
                }
                return list;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ==================== 工具方法 ====================

    /** 统一 GET 拉列表 */
    private List<JSONObject> queryList(String path, Map<String, Object> params) {
        try {
            String resp = doGet(path, params);
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("code") == 200) {
                JSONArray arr = json.getJSONArray("data");
                List<JSONObject> list = new ArrayList<>();
                if (arr != null) {
                    for (Object o : arr) list.add((JSONObject) o);
                }
                return list;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}