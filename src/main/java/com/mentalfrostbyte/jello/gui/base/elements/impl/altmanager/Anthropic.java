package com.mentalfrostbyte.jello.gui.base.elements.impl.altmanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class Anthropic {

    public interface MicrosoftAccountValidator {
        boolean isPremium(Account account);
    }

    private static final MicrosoftAccountValidator DEFAULT_VALIDATOR =
            new XboxLiveValidator();

    private static volatile MicrosoftAccountValidator validator =
            DEFAULT_VALIDATOR;

    public static final class XboxLiveValidator
            implements MicrosoftAccountValidator {

        private static final String XBL_AUTH_URL =
                "https://user.auth.xboxlive.com/user/authenticate";

        private static final String XSTS_AUTH_URL =
                "https://xsts.auth.xboxlive.com/xsts/authorize";

        private static final String MC_LOGIN_URL =
                "https://api.minecraftservices.com/authentication/login_with_xbox";

        private static final String MC_PROFILE_URL =
                "https://api.minecraftservices.com/minecraft/profile";

        private static final int CONNECT_TIMEOUT = 5000;
        private static final int READ_TIMEOUT = 5000;

        @Override
        public boolean isPremium(Account account) {
            if (account == null) {
                return false;
            }

            String token = account.getToken();

            if (token == null || token.trim().isEmpty()) {
                return false;
            }

            token = token.trim();

            /*
             * 很多 AltManager 的 Account#getToken()
             * 保存的已经是最终 Minecraft access token。
             *
             * 所以先直接尝试 Minecraft profile。
             */
            if (hasMinecraftProfile(token)) {
                return true;
            }

            /*
             * 如果不是 Minecraft token，
             * 再尝试把它当成 Microsoft OAuth token。
             */
            try {

                // =========================
                // 1. Microsoft -> Xbox Live
                // =========================

                JsonObject xblProperties = new JsonObject();
                xblProperties.addProperty(
                        "AuthMethod",
                        "RPS"
                );
                xblProperties.addProperty(
                        "SiteName",
                        "user.auth.xboxlive.com"
                );
                xblProperties.addProperty(
                        "RpsTicket",
                        "d=" + token
                );

                JsonObject xblRequest = new JsonObject();
                xblRequest.add(
                        "Properties",
                        xblProperties
                );
                xblRequest.addProperty(
                        "RelyingParty",
                        "http://auth.xboxlive.com"
                );
                xblRequest.addProperty(
                        "TokenType",
                        "JWT"
                );

                JsonObject xblResponse =
                        postJson(
                                XBL_AUTH_URL,
                                xblRequest,
                                true
                        );

                String xblToken =
                        getRequiredString(
                                xblResponse,
                                "Token"
                        );

                // =========================
                // 2. Xbox Live -> XSTS
                // =========================

                JsonArray userTokens =
                        new JsonArray();

                userTokens.add(xblToken);

                JsonObject xstsProperties =
                        new JsonObject();

                xstsProperties.addProperty(
                        "SandboxId",
                        "RETAIL"
                );

                xstsProperties.add(
                        "UserTokens",
                        userTokens
                );

                JsonObject xstsRequest =
                        new JsonObject();

                xstsRequest.add(
                        "Properties",
                        xstsProperties
                );

                xstsRequest.addProperty(
                        "RelyingParty",
                        "rp://api.minecraftservices.com/"
                );

                xstsRequest.addProperty(
                        "TokenType",
                        "JWT"
                );

                JsonObject xstsResponse =
                        postJson(
                                XSTS_AUTH_URL,
                                xstsRequest,
                                true
                        );

                String xstsToken =
                        getRequiredString(
                                xstsResponse,
                                "Token"
                        );

                String userHash =
                        xstsResponse
                                .getAsJsonObject(
                                        "DisplayClaims"
                                )
                                .getAsJsonArray(
                                        "xui"
                                )
                                .get(0)
                                .getAsJsonObject()
                                .get("uhs")
                                .getAsString();

                // =========================
                // 3. XSTS -> Minecraft
                // =========================

                JsonObject minecraftRequest =
                        new JsonObject();

                minecraftRequest.addProperty(
                        "identityToken",
                        "XBL3.0 x="
                                + userHash
                                + ";"
                                + xstsToken
                );

                JsonObject minecraftResponse =
                        postJson(
                                MC_LOGIN_URL,
                                minecraftRequest,
                                false
                        );

                String minecraftToken =
                        getRequiredString(
                                minecraftResponse,
                                "access_token"
                        );

                // =========================
                // 4. 验证 Minecraft Profile
                // =========================

                return hasMinecraftProfile(
                        minecraftToken
                );

            } catch (Exception ignored) {
                return false;
            }
        }

        /**
         * 使用 Minecraft Access Token 请求玩家 Profile。
         *
         * HTTP 200:
         * token 有效并且存在 Minecraft Java Profile。
         */
        private static boolean hasMinecraftProfile(
                String minecraftToken
        ) {

            HttpURLConnection connection = null;

            try {
                connection =
                        openConnection(
                                MC_PROFILE_URL,
                                "GET"
                        );

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + minecraftToken
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                return connection.getResponseCode()
                        == HttpURLConnection.HTTP_OK;

            } catch (IOException ignored) {
                return false;

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static JsonObject postJson(
                String url,
                JsonObject body,
                boolean xboxContractHeader
        ) throws IOException {

            HttpURLConnection connection = null;

            try {
                connection =
                        openConnection(
                                url,
                                "POST"
                        );

                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                if (xboxContractHeader) {
                    connection.setRequestProperty(
                            "x-xbl-contract-version",
                            "1"
                    );
                }

                byte[] payload =
                        body.toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                try (OutputStream output =
                             connection.getOutputStream()) {

                    output.write(payload);
                }

                int status =
                        connection.getResponseCode();

                InputStream stream =
                        status >= 200 && status < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String response =
                        readFully(stream);

                if (status < 200 || status >= 300) {
                    throw new IOException(
                            "HTTP "
                                    + status
                                    + " from "
                                    + url
                    );
                }

                return new JsonParser()
                        .parse(response)
                        .getAsJsonObject();

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static HttpURLConnection openConnection(
                String url,
                String method
        ) throws IOException {

            HttpURLConnection connection =
                    (HttpURLConnection)
                            new URL(url)
                                    .openConnection();

            connection.setRequestMethod(method);

            connection.setConnectTimeout(
                    CONNECT_TIMEOUT
            );

            connection.setReadTimeout(
                    READ_TIMEOUT
            );

            connection.setUseCaches(false);

            return connection;
        }

        private static String readFully(
                InputStream stream
        ) throws IOException {

            if (stream == null) {
                return "";
            }

            StringBuilder result =
                    new StringBuilder();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         stream,
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                String line;

                while ((line =
                        reader.readLine())
                        != null) {

                    result.append(line);
                }
            }

            return result.toString();
        }

        private static String getRequiredString(
                JsonObject object,
                String memberName
        ) throws IOException {

            if (object == null
                    || !object.has(memberName)
                    || object.get(memberName)
                    .isJsonNull()) {

                throw new IOException(
                        "Missing JSON field: "
                                + memberName
                );
            }

            return object
                    .get(memberName)
                    .getAsString();
        }
    }

    private Anthropic() {
    }

    public static boolean isMicrosoftAccount(
            Account account
    ) {
        return validator.isPremium(account);
    }

    /**
     * Returns the cached premium-account status without any network I/O.
     *
     * @param account the account to query
     * @return {@link Boolean#TRUE} if the account was validated as a Microsoft
     *         (premium) account, {@link Boolean#FALSE} if it was validated as
     *         cracked/offline, or {@code null} if it has not been validated yet
     */
    public static Boolean getCachedStatus(Account account) {
        if (account == null) {
            return Boolean.FALSE;
        }
        return account.getPremiumStatus();
    }

    /**
     * Convenience predicate that never blocks on the network: returns the cached
     * result if present, and falls back to a cheap local heuristic (non-empty
     * token) when the account has not been validated yet.
     *
     * @param account the account to query
     * @return true if the account looks like a premium (Microsoft) account
     */
    public static boolean isPremiumCached(Account account) {
        Boolean cached = getCachedStatus(account);
        if (cached != null) {
            return cached;
        }
        if (account == null) {
            return false;
        }
        String token = account.getToken();
        return token != null && !token.trim().isEmpty();
    }

    /**
     * Kicks off an asynchronous validation of the account on a daemon background
     * thread. The result is written into the account's cached status, which the
     * render thread can read via {@link #getCachedStatus(Account)} later, so the
     * UI never blocks on the Xbox/Minecraft API calls.
     * <p>
     * If the account already has a cached status, this is a no-op.
     *
     * @param account the account to validate, may be {@code null}
     */
    public static void validateAsync(Account account) {
        if (account == null) {
            return;
        }
        if (account.getPremiumStatus() != null) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                boolean premium = validator.isPremium(account);
                account.setPremiumStatus(premium);
            } catch (Exception ignored) {
                account.setPremiumStatus(Boolean.FALSE);
            }
        }, "PremiumValidator-" + System.nanoTime());
        worker.setDaemon(true);
        worker.start();
    }

    public static void setValidator(
            MicrosoftAccountValidator customValidator
    ) {
        validator =
                customValidator != null
                        ? customValidator
                        : DEFAULT_VALIDATOR;
    }

    public static MicrosoftAccountValidator getValidator() {
        return validator;
    }
}