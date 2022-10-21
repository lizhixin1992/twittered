package io.github.redouane59.twitter.utils;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 全局的工具类
 *
 */
public class GlobalAuthUtils {
    private static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    /**
     * 签名
     *
     * @param key       key
     * @param data      data
     * @param algorithm algorithm
     * @return byte[]
     */
    private static byte[] sign(byte[] key, byte[] data, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Unsupported algorithm: " + algorithm, ex);
        } catch (InvalidKeyException ex) {
            throw new RuntimeException("Invalid key: " + Arrays.toString(key), ex);
        }
    }

    /**
     * 编码
     *
     * @param value str
     * @return encode str
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            String encoded = URLEncoder.encode(value, GlobalAuthUtils.DEFAULT_ENCODING.displayName());
            return encoded.replace("+", "%20").replace("*", "%2A").replace("~", "%7E").replace("/", "%2F");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed To Encode Uri", e);
        }
    }

    /**
     * map转字符串，转换后的字符串格式为 {@code xxx=xxx&xxx=xxx}
     *
     * @param params 待转换的map
     * @param encode 是否转码
     * @return str
     */
    public static String parseMapToString(Map<String, String> params, boolean encode) {
        if (null == params || params.isEmpty()) {
            return "";
        }
        List<String> paramList = new ArrayList<>();
        params.forEach((k, v) -> {
            if (null == v) {
                paramList.add(k + "=");
            } else {
                paramList.add(k + "=" + (encode ? urlEncode(v) : v));
            }
        });
        return String.join("&", paramList);
    }

    /**
     * Generate nonce with given length
     *
     * @param len length
     * @return nonce string
     */
    public static String generateNonce(int len) {
        String s = "0123456789QWERTYUIOPLKJHGFDSAZXCVBNMqwertyuioplkjhgfdsazxcvbnm";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int index = rng.nextInt(62);
            sb.append(s, index, index + 1);
        }
        return sb.toString();
    }

    /**
     * Get current timestamp
     *
     * @return timestamp string
     */
    public static String getTimestamp() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    /**
     * Generate Twitter signature
     * https://developer.twitter.com/en/docs/basics/authentication/guides/creating-a-signature
     *
     * @param params      parameters including: oauth headers, query params, body params
     * @param method      HTTP method
     * @param baseUrl     base url
     * @param apiSecret   api key secret can be found in the developer portal by viewing the app details page
     * @param tokenSecret oauth token secret
     * @return BASE64 encoded signature string
     */
    public static String generateTwitterSignature(Map<String, String> params, String method, String baseUrl, String apiSecret, String tokenSecret) {
        TreeMap<String, String> map = new TreeMap<>(params);
        String str = parseMapToString(map, true);
        String baseStr = method.toUpperCase() + "&" + urlEncode(baseUrl) + "&" + urlEncode(str);
        String signKey = apiSecret + "&" + (StringUtils.isEmpty(tokenSecret) ? "" : tokenSecret);
        byte[] signature = sign(signKey.getBytes(DEFAULT_ENCODING), baseStr.getBytes(DEFAULT_ENCODING), HMAC_SHA1);

        return new String(Base64Utils.encode(signature, false));
    }

}
