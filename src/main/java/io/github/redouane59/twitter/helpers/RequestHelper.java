package io.github.redouane59.twitter.helpers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.scribejava.core.httpclient.multipart.FileByteArrayBodyPartPayload;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import io.github.redouane59.twitter.dto.tweet.MediaCategory;
import io.github.redouane59.twitter.dto.tweet.UploadMediaResponse;
import io.github.redouane59.twitter.dto.tweet.UploadedMedia;
import io.github.redouane59.twitter.signature.TwitterCredentials;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
public class RequestHelper extends AbstractRequestHelper {

  public RequestHelper(TwitterCredentials twitterCredentials) {
    super(twitterCredentials);
  }

  public RequestHelper(TwitterCredentials twitterCredentials, OAuth10aService service) {
    super(twitterCredentials, service);
  }

  public <T> Optional<T> postRequestWithBodyJson(String url, Map<String, String> parameters, String requestBodyJson, Class<T> classType) {
    return makeRequest(Verb.POST, url, parameters, requestBodyJson, true, classType);
  }

  public <T> Optional<T> postRequest(String url, Map<String, String> parameters, Class<T> classType) {
    return postRequestWithBodyJson(url, parameters, null, classType);
  }

  public <T> Optional<T> postRequestWithoutSign(String url, Map<String, String> parameters, Class<T> classType) {
    return makeRequest(Verb.POST, url, parameters, null, false, classType);
  }

  public <T> Optional<T> uploadMedia(String url, File file, Class<T> classType) {
    try {
      return uploadMedia(url, file.getName(), Files.readAllBytes(file.toPath()), classType);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  public <T> Optional<T> uploadMedia(String url, String fileName, byte[] data, Class<T> classType) {
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    request.initMultipartPayload();
    request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("application/octet-stream", data, "media", fileName));
    return makeRequest(request, true, classType);
  }

  public <T> Optional<T> putRequest(String url, String body, Class<T> classType) {
    return makeRequest(Verb.PUT, url, null, body, true, classType);
  }

  @Override
  public <T> Optional<T> getRequest(String url, Class<T> classType) {
    return getRequestWithParameters(url, null, classType);
  }


  @Override
  public <T> Optional<T> getRequestWithParameters(String url, Map<String, String> parameters, Class<T> classType) {
    return makeRequest(Verb.GET, url, parameters, null, true, classType);
  }

  @Override
  protected void signRequest(OAuthRequest request) {
    getService().signRequest(getTwitterCredentials().asAccessToken(), request);
  }

  /****************************************twitter 媒体分片上传*************************************************************/
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
   * // max chunk size
   */
  private final int CHUNK_SIZE = 2 * MB;

  /**
   * 分片上传媒体信息（video）
   *
   * @author lizhixin
   * @date 2022/4/28 13:15
   */
  public <T> Optional<T> uploadMediaChunked(String url, String fileName, InputStream media, Class<T> classType, String mediaCategory) throws Exception {
    byte[] dataBytes = null;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
      byte[] buffer = new byte[32768];
      int n;
      while ((n = media.read(buffer)) != -1) {
        baos.write(buffer, 0, n);
      }
      dataBytes = baos.toByteArray();
      if(MediaCategory.AMPLIFY_VIDEO.label.equals(mediaCategory)){
        if (dataBytes.length > MAX_VIDEO_SIZE) {
          LOGGER.error(String.format(Locale.US,
                  "video file can't be longer than: %d MBytes",
                  MAX_VIDEO_SIZE / MB));
          throw new RuntimeException("video file can't be longer than: " + MAX_VIDEO_SIZE / MB + " MBytes");
        }
      }else if(MediaCategory.TWEET_GIF.label.equals(mediaCategory)){
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
      Optional<UploadMediaResponse> initUploadMediaResponse = uploadMediaChunkedInit(dataBytes.length, url, mediaCategory);
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
        uploadMediaChunkedAppend(fileName, byteArray, segmentIndex, initUploadMediaResponse.get().getMediaId(), url);

        segmentData = new byte[CHUNK_SIZE];
        segmentIndex++;
      }
      //分片信息发送完后，通知twitter，反查文件上传状态，等待twitter通知
      UploadedMedia uploadedMedia = uploadMediaChunkedFinalize(Long.parseLong(initUploadMediaResponse.get().getMediaId()), url, dataBytes.length);
      UploadMediaResponse uploadMediaResponse = new UploadMediaResponse();
      uploadMediaResponse.setMediaId(String.valueOf(uploadedMedia.getMediaId()));
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
  private Optional<UploadMediaResponse> uploadMediaChunkedInit(long size, String url,String mediaCategory) {
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    request.addBodyParameter("command", CHUNKED_INIT);
    if(MediaCategory.AMPLIFY_VIDEO.label.equals(mediaCategory)){
      request.addBodyParameter("media_type", "video/mp4");
    }else if(MediaCategory.TWEET_GIF.label.equals(mediaCategory)){
      request.addBodyParameter("media_type", "image/gif");
    }
    request.addBodyParameter("media_category", mediaCategory);
    request.addBodyParameter("total_bytes", String.valueOf(size));
    Optional<UploadMediaResponse> initUploadMediaResponse = makeRequest(request, true, UploadMediaResponse.class);
    LOGGER.info("mediaId : {}, mediaKey : {}, expiresAfterSecs : {}, size : {}",
            initUploadMediaResponse.get().getMediaId(),
            initUploadMediaResponse.get().getMediaKey(),
            initUploadMediaResponse.get().getExpiresAfterSecs(),
            initUploadMediaResponse.get().getSize());
    return initUploadMediaResponse;
  }

  /**
   * 分片上传文件
   *
   * @author lizhixin
   * @date 2022/4/28 13:31
   */
  private void uploadMediaChunkedAppend(String fileName, byte[] byteArray, int segmentIndex, String mediaId, String url) {
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    request.initMultipartPayload();
    request.addHeader("Content-Type", "multipart/form-data");
    request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", CHUNKED_APPEND.getBytes(StandardCharsets.UTF_8), "command"));
    request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", mediaId.getBytes(StandardCharsets.UTF_8), "media_id"));
    request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", String.valueOf(segmentIndex).getBytes(StandardCharsets.UTF_8), "segment_index"));
    request.addBodyPartPayloadInMultipartPayload(new FileByteArrayBodyPartPayload("form-data", byteArray, "media", fileName));
    makeRequest(request, true);
  }

  /**
   * 分片信息发送完后，通知twitter，反查文件上传状态，等待twitter通知
   *
   * @author lizhixin
   * @date 2022/4/28 13:31
   */
  private UploadedMedia uploadMediaChunkedFinalize(long mediaId, String url, Integer fileSize) throws Exception {
    int tries = 0;
    int maxTries = 20;
    int lastProgressPercent = 0;
    int currentProgressPercent = 0;
    //通知twitter发送完成 FINALIZE
    UploadedMedia uploadMediaChunkedFinalize0 = uploadMediaChunkedFinalize0(mediaId, url);

    while (tries < maxTries) {
      if (lastProgressPercent == currentProgressPercent) {
        tries++;
      }
      lastProgressPercent = currentProgressPercent;
      String state = uploadMediaChunkedFinalize0.getProcessingState();
      if (state.equals("failed")) {
        LOGGER.error("Failed to finalize the chuncked upload.");
        throw new RuntimeException("Failed to finalize the chuncked upload.");
      }
      if (state.equals("pending") || state.equals("in_progress")) {
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
      if (state.equals("succeeded")) {
        return uploadMediaChunkedFinalize0;
      }
      //查询文件上传状态
      uploadMediaChunkedFinalize0 = uploadMediaChunkedStatus(mediaId, url);
    }
    LOGGER.error("Failed to finalize the chuncked upload, progress has stopped, tried " + tries + 1 + " times.");
    throw new RuntimeException("Failed to finalize the chuncked upload, progress has stopped, tried " + tries + 1 + " times.");
  }

  /**
   * 通知twitter发送完成 FINALIZE
   *
   * @author lizhixin
   * @date 2022/4/28 13:31
   */
  private UploadedMedia uploadMediaChunkedFinalize0(long mediaId, String url) throws Exception {
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    request.addBodyParameter("command", CHUNKED_FINALIZE);
    request.addBodyParameter("media_id", String.valueOf(mediaId));
    String chunkedFinalize = makeRequest(request, true);
    LOGGER.info("Finalize response:" + chunkedFinalize);
    return new UploadedMedia(JSON.parseObject(chunkedFinalize));
  }

  /**
   * 查询文件上传状态
   *
   * @author lizhixin
   * @date 2022/4/28 13:31
   */
  private UploadedMedia uploadMediaChunkedStatus(long mediaId, String url) throws Exception {
    OAuthRequest request = new OAuthRequest(Verb.GET, url);
    request.addQuerystringParameter("command", CHUNKED_STATUS);
    request.addQuerystringParameter("media_id", String.valueOf(mediaId));
    String chunkedFinalize00 = makeRequest(request, true);
    LOGGER.info("Status response:" + chunkedFinalize00);
    return new UploadedMedia(JSON.parseObject(chunkedFinalize00));
  }
}
