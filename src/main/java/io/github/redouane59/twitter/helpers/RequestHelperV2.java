package io.github.redouane59.twitter.helpers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.scribejava.core.httpclient.multipart.FileByteArrayBodyPartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import io.github.redouane59.twitter.IAPIEventListener;
import io.github.redouane59.twitter.dto.others.BearerToken;
import io.github.redouane59.twitter.dto.tweet.MediaCategory;
import io.github.redouane59.twitter.dto.tweet.Tweet;
import io.github.redouane59.twitter.dto.tweet.TweetV2;
import io.github.redouane59.twitter.dto.tweet.UploadMediaResponse;
import io.github.redouane59.twitter.dto.tweet.UploadMediaResponseV2;
import io.github.redouane59.twitter.dto.tweet.UploadedMedia;
import io.github.redouane59.twitter.dto.tweet.UploadedMediaV2;
import io.github.redouane59.twitter.signature.Scope;
import io.github.redouane59.twitter.signature.TwitterCredentials;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

@Slf4j
public class RequestHelperV2 extends AbstractRequestHelper {

  public RequestHelperV2(TwitterCredentials twitterCredentials) {
    super(twitterCredentials);
  }

  public RequestHelperV2(TwitterCredentials twitterCredentials, OAuth10aService service) {
    super(twitterCredentials, service);
  }

  @Override
  public <T> Optional<T> getRequest(String url, Class<T> classType) {
    return getRequestWithParameters(url, null, classType);
  }

  @Override
  public <T> Optional<T> getRequestWithParameters(String url, Map<String, String> parameters, Class<T> classType) {
    return makeRequest(Verb.GET, url, parameters, null, true, classType);
  }

  public Future<Response> getAsyncRequest(String url, Map<String, String> parameters, Consumer<Tweet> consumer) {
    // All the stream are handled internally with an IAPIEventListener.
    IAPIEventListener listener = new IAPIEventListener() {

      @Override
      public void onStreamError(int httpCode, String error) {
        //
      }

      @Override
      public void onTweetStreamed(Tweet tweet) {
        consumer.accept(tweet);
      }

      @Override
      public void onUnknownDataStreamed(String json) {
        //
      }

      @Override
      public void onStreamEnded(Exception e) {
        //
      }

    };

    return getAsyncRequest(url, parameters, listener, TweetV2.class);
  }

  public Future<Response> getAsyncRequest(String url, Map<String, String> parameters, IAPIEventListener listener) {
    return getAsyncRequest(url, parameters, listener, TweetV2.class);
  }

  public <T> Future<Response> getAsyncRequest(String url,
                                              Map<String, String> parameters,
                                              IAPIEventListener listener,
                                              final Class<? extends T> targetClass) {
    if (parameters != null) {
      url += parameters.entrySet().stream()
                       .map(p -> p.getKey() + "=" + p.getValue())
                       .reduce((p1, p2) -> p1 + "&" + p2)
                       .map(s -> "?" + s)
                       .orElse("");
    }
    OAuthRequest request = new OAuthRequest(Verb.GET, url);
    signRequest(request);
    return getService().execute(request, new OAuthAsyncRequestCallback<Response>() {

      @Override
      public void onThrowable(Throwable t) {
        LOGGER.error(t.getMessage(), t);
      }

      @Override
      public void onCompleted(Response response) {
        try {
          tweetStreamConsumer.consumeStream(listener, response, targetClass);
        } catch (Exception e) {
          onThrowable(e);
        }
      }
    });
  }

  public <T> Optional<T> postRequest(String url, String body, Class<T> classType) {
    return makeRequest(Verb.POST, url, null, body, true, classType);
  }

  public <T> Optional<T> postRequestWithHeader(String url, Map<String, String> headersMap, String body, Class<T> classType) {
    return makeRequest(Verb.POST, url, headersMap, null, body, false, classType);
  }

  public <T> Optional<T> getRequestWithHeader(String url, Map<String, String> headersMap, Class<T> classType) {
    return makeRequest(Verb.GET, url, headersMap, null, null, false, classType);
  }

  @Override
  protected void signRequest(OAuthRequest request) {
    request.addHeader(OAuthConstants.HEADER, "Bearer " + getPKCEBearerToken());
  }

  public String getBearerToken() {
    if (getTwitterCredentials().getBearerToken() == null) {
      String url = URLHelper.GET_BEARER_TOKEN_URL;
      String valueToCrypt = getTwitterCredentials().getApiKey()
                            + ":" + getTwitterCredentials().getApiSecretKey();
      String              cryptedValue = Base64.getEncoder().encodeToString(valueToCrypt.getBytes());
      Map<String, String> headers      = new HashMap<>();
      headers.put("Authorization", "Basic " + cryptedValue);
      headers.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
      String                body   = "grant_type=client_credentials";
      Optional<BearerToken> result = makeRequest(Verb.POST, url, headers, null, body, false, BearerToken.class);
      getTwitterCredentials().setBearerToken(result.orElseThrow(NoSuchElementException::new).getAccessToken());
    }
    return getTwitterCredentials().getBearerToken();
  }

    public String getPKCEBearerToken() {
        return getTwitterCredentials().getBearerToken();
    }

  /**
   * @param clientId Can be found in the developer portal under the header "Client ID".
   * @param redirectUri Your callback URL. This value must correspond to one of the Callback URLs defined in your App’s settings. For OAuth 2.0, you
   * will need to have exact match validation for your callback URL.
   * @param state A random string you provide to verify against CSRF attacks.
   * @param codeChallenge A PKCE parameter, a random secret for each request you make. You can use this tooling to generate an s256 PKCE code.
   * @param codeChallengeMethod Specifies the method you are using to make a request (s256 OR plain).
   * @return .
   */
  @SneakyThrows
  public String getAuthorizeUrl(String clientId,
                                String redirectUri,
                                String state,
                                String codeChallenge,
                                String codeChallengeMethod,
                                List<Scope> scopes) {

    Map<String, String> mapParams = new HashMap<>();
    mapParams.put("response_type", "code");
    mapParams.put("client_id", clientId);
    mapParams.put("redirect_uri", redirectUri);
    mapParams.put("state", state);
    mapParams.put("code_challenge", codeChallenge);
    mapParams.put("code_challenge_method", codeChallengeMethod);
    mapParams.put("grant_type", "refresh_token");
    mapParams.put("scope", scopes.stream().map(Scope::getName).collect(Collectors.joining(" ")));

    List<NameValuePair> queryParams = new ArrayList<>();
    for (Entry<String, String> entry : mapParams.entrySet()) {
      queryParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
    }

    URIBuilder builder = new URIBuilder()
        .setScheme("https")
        .setHost("twitter.com/i/oauth2/authorize")
        .setParameters(queryParams);

    return builder.build().toString();
  }


    /****************************************twitter V2版本方法*************************************************************/
    private final String CHUNKED_INIT = "INIT";
    private final String CHUNKED_APPEND = "APPEND";
    private final String CHUNKED_FINALIZE = "FINALIZE";
    private final String CHUNKED_STATUS = "STATUS";
    /**
     * 1 MByte
     */
    private final int MB = 1024 * 1024;
    /**
     * 512MB is a constraint  imposed by Twitter for video files
     */
    private final int MAX_VIDEO_SIZE = 512 * MB;
    /**
     * 15MB is a constraint  imposed by Twitter for gif files
     */
    private final int MAX_GIF_SIZE = 15 * MB;
    /**
     * max chunk size
     */
    private final int CHUNK_SIZE = 2 * MB;

    /**
     * 分片上传媒体文件
     *
     * @author lizhixin
     * @date 2025/3/20 11:59
     */
    public <T> Optional<T> uploadMediaChunkedV2(String url, String fileName, InputStream media, Class<T> classType, String mediaCategory) throws Exception {
        byte[] dataBytes;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
            byte[] buffer = new byte[32768];
            int n;
            while ((n = media.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
            dataBytes = baos.toByteArray();
            if (MediaCategory.AMPLIFY_VIDEO.label.equals(mediaCategory)) {
                if (dataBytes.length > MAX_VIDEO_SIZE) {
                    LOGGER.error(String.format(Locale.US,
                            "video file can't be longer than: %d MBytes",
                            MAX_VIDEO_SIZE / MB));
                    throw new RuntimeException("video file can't be longer than: " + MAX_VIDEO_SIZE / MB + " MBytes");
                }
            } else if (MediaCategory.TWEET_GIF.label.equals(mediaCategory)) {
                if (dataBytes.length > MAX_GIF_SIZE) {
                    LOGGER.error(String.format(Locale.US,
                            "gif file can't be longer than: %d MBytes",
                            MAX_GIF_SIZE / MB));
                    throw new RuntimeException("gif file can't be longer than: " + MAX_GIF_SIZE / MB + " MBytes");
                }
            }

        } catch (IOException ioe) {
            LOGGER.error("Failed to download the file.", ioe);
            throw new RuntimeException("Failed to download the file.", ioe);
        }

        try {
            //初始化 init
            Optional<UploadMediaResponseV2> initUploadMediaResponse = uploadMediaChunkedInitV2(dataBytes.length, url, mediaCategory);
            ByteArrayInputStream dataInputStream = new ByteArrayInputStream(dataBytes);

            byte[] segmentData = new byte[CHUNK_SIZE];
            int segmentIndex = 0;
            int totalRead = 0;
            int bytesRead = 0;

            //分片上传文件
            while ((bytesRead = dataInputStream.read(segmentData)) > 0) {
                totalRead = totalRead + bytesRead;
                LOGGER.info("Chunked appened, segment index:" + segmentIndex + " bytes:" + totalRead + "/" + dataBytes.length);
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(segmentData, 0, bytesRead);
                byte[] byteArray = IOUtils.toByteArray(byteArrayInputStream);
                uploadMediaChunkedAppendV2(fileName, byteArray, segmentIndex, initUploadMediaResponse.get().getData().getId(), url);

                segmentData = new byte[CHUNK_SIZE];
                segmentIndex++;
            }
            //分片信息发送完后，通知twitter，反查文件上传状态，等待twitter通知
            UploadedMediaV2 uploadedMedia = uploadMediaChunkedFinalizeV2(initUploadMediaResponse.get().getData().getId(), url, dataBytes.length);
            UploadMediaResponse uploadMediaResponse = new UploadMediaResponse();
            uploadMediaResponse.setMediaId(String.valueOf(uploadedMedia.getId()));
            return (Optional<T>) Optional.ofNullable(uploadMediaResponse);
        } catch (Exception e) {
            LOGGER.error("uploadMediaChunked is error.", e);
            throw new RuntimeException("uploadMediaChunked is error..", e);
        }
    }

    /**
     * twitter文件上传初始化 init
     *
     * @author lizhixin
     * @date 2022/4/28 13:32
     */
    private Optional<UploadMediaResponseV2> uploadMediaChunkedInitV2(long size, String url, String mediaCategory) {
//        OAuthRequest request = new OAuthRequest(Verb.POST, url);
//        request.addBodyParameter("command", CHUNKED_INIT);
//        if (MediaCategory.AMPLIFY_VIDEO.label.equals(mediaCategory)) {
//            request.addBodyParameter("media_type", "video/mp4");
//        } else if (MediaCategory.TWEET_GIF.label.equals(mediaCategory)) {
//            request.addBodyParameter("media_type", "image/gif");
//        }
//        request.addBodyParameter("media_category", mediaCategory);
//        request.addBodyParameter("total_bytes", String.valueOf(size));
//        Optional<UploadMediaResponseV2> initUploadMediaResponse = makeRequest(request, true, UploadMediaResponseV2.class);
//        LOGGER.info("mediaId : {}, mediaKey : {}, expiresAfterSecs : {}, size : {}",
//                initUploadMediaResponse.get().getData().getId(),
//                initUploadMediaResponse.get().getData().getMediaKey(),
//                initUploadMediaResponse.get().getData().getExpiresAfterSecs(),
//                initUploadMediaResponse.get().getData().getSize());
//        return initUploadMediaResponse;

        JSONObject body = new JSONObject();
        if (MediaCategory.AMPLIFY_VIDEO.label.equals(mediaCategory)) {
            body.put("media_type", "video/mp4");
        } else if (MediaCategory.TWEET_GIF.label.equals(mediaCategory)) {
            body.put("media_type", "image/gif");
        }
        body.put("media_category", mediaCategory);
        body.put("total_bytes", size);

        Optional<UploadMediaResponseV2> initUploadMediaResponse = makeRequest(Verb.POST, url + "/initialize", null, body.toJSONString(), true, UploadMediaResponseV2.class);
        LOGGER.info("mediaId : {}, mediaKey : {}, expiresAfterSecs : {}, size : {}",
                initUploadMediaResponse.get().getData().getId(),
                initUploadMediaResponse.get().getData().getMediaKey(),
                initUploadMediaResponse.get().getData().getExpiresAfterSecs(),
                initUploadMediaResponse.get().getData().getSize());
        return initUploadMediaResponse;
    }

    /**
     * 分片上传文件
     *
     * @author lizhixin
     * @date 2022/4/28 13:31
     */
    private void uploadMediaChunkedAppendV2(String fileName, byte[] byteArray, int segmentIndex, String mediaId, String url) {
        OAuthRequest request = new OAuthRequest(Verb.POST, url + "/" + mediaId + "/append");
        request.initMultipartPayload();
        request.addHeader("Content-Type", "multipart/form-data");
//        request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", CHUNKED_APPEND.getBytes(StandardCharsets.UTF_8), "command"));
        request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", mediaId.getBytes(StandardCharsets.UTF_8), "media_id"));
        request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", String.valueOf(segmentIndex).getBytes(StandardCharsets.UTF_8), "segment_index"));
        request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", byteArray, "media", fileName));
        makeRequest(request, true);

//        JSONObject body  = new JSONObject();
//        body.put("media_id", mediaId);
//        body.put("segment_index", segmentIndex);
//        body.put("media", byteArray);
//        makeRequest(Verb.POST, url + "/" + mediaId + "/append", null, body.toJSONString(), true);
    }

    /**
     * 分片信息发送完后，通知twitter，反查文件上传状态，等待twitter通知
     *
     * @author lizhixin
     * @date 2022/4/28 13:31
     */
    private UploadedMediaV2 uploadMediaChunkedFinalizeV2(String mediaId, String url, Integer fileSize) throws Exception {
        int tries = 0;
        int maxTries = 20;
        int lastProgressPercent = 0;
        int currentProgressPercent = 0;
        //通知twitter发送完成 FINALIZE
        UploadedMediaV2 uploadMediaChunkedFinalize0 = uploadMediaChunkedFinalize0V2(mediaId, url);

        // 如果没有 processing_info 字段，则认为上传成功, 直接返回
        if(StringUtils.isNotBlank(uploadMediaChunkedFinalize0.getProcessingState())){
            while (tries < maxTries) {
                if (lastProgressPercent == currentProgressPercent) {
                    tries++;
                }
                lastProgressPercent = currentProgressPercent;
                String state = uploadMediaChunkedFinalize0.getProcessingState();
                if (("failed").equalsIgnoreCase(state)) {
                    LOGGER.error("Failed to finalize the chuncked upload.");
                    throw new RuntimeException("Failed to finalize the chuncked upload.");
                }
                if (("pending").equalsIgnoreCase(state) || ("in_progress").equalsIgnoreCase(state)) {
                    currentProgressPercent = Objects.isNull(uploadMediaChunkedFinalize0.getProgressPercent()) ? 0 : uploadMediaChunkedFinalize0.getProgressPercent();
                    int waitSec = Math.max(uploadMediaChunkedFinalize0.getProcessingCheckAfterSecs(), 1);
                    LOGGER.info("Chunked finalize, wait for:" + waitSec + " sec");
                    try {
                        Thread.sleep(waitSec * 1000);
                    } catch (InterruptedException e) {
                        LOGGER.error("Failed to finalize the chuncked upload.", e);
                        throw new RuntimeException("Failed to finalize the chuncked upload.", e);
                    }
                }
                if (("succeeded").equalsIgnoreCase(state)) {
                    return uploadMediaChunkedFinalize0;
                }
                //查询文件上传状态
                uploadMediaChunkedFinalize0 = uploadMediaChunkedStatusV2(mediaId, url);
            }
            LOGGER.error("Failed to finalize the chuncked upload, progress has stopped, tried " + tries + 1 + " times.");
            throw new RuntimeException("Failed to finalize the chuncked upload, progress has stopped, tried " + tries + 1 + " times.");
        }else{
            return uploadMediaChunkedFinalize0;
        }
    }

    /**
     * 通知twitter发送完成 FINALIZE
     *
     * @author lizhixin
     * @date 2022/4/28 13:31
     */
    private UploadedMediaV2 uploadMediaChunkedFinalize0V2(String mediaId, String url) throws Exception {
//        OAuthRequest request = new OAuthRequest(Verb.POST, url);
//        request.addBodyParameter("command", CHUNKED_FINALIZE);
//        request.addBodyParameter("media_id", mediaId);
//        String chunkedFinalize = makeRequest(request, true);
//        LOGGER.info("Finalize response:" + chunkedFinalize);
//        return new UploadedMediaV2(JSON.parseObject(chunkedFinalize));

        String chunkedFinalize = makeRequest(Verb.POST, url + "/" + mediaId + "/finalize", null, null, true);
        LOGGER.info("Finalize response:" + chunkedFinalize);
        return new UploadedMediaV2(JSON.parseObject(chunkedFinalize));
    }

    /**
     * 查询文件上传状态
     *
     * @author lizhixin
     * @date 2022/4/28 13:31
     */
    private UploadedMediaV2 uploadMediaChunkedStatusV2(String mediaId, String url) throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        request.addQuerystringParameter("command", CHUNKED_STATUS);
        request.addQuerystringParameter("media_id", mediaId);
        String chunkedFinalize00 = makeRequest(request, true);
        LOGGER.info("Status response:" + chunkedFinalize00);
        return new UploadedMediaV2(JSON.parseObject(chunkedFinalize00));
    }

    public <T> Optional<T> postRequestWithBodyJson(String url, Map<String, String> parameters, String requestBodyJson, Class<T> classType) {
        return makeRequest(Verb.POST, url, parameters, requestBodyJson, true, classType);
    }
}
