package io.github.redouane59.twitter.dto.tweet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UploadMediaResponseV2 {

  private Data data;

  @Builder
  @Getter
  @Jacksonized
  public static class Data {
    private String id;
    @JsonProperty("expires_after_secs")
    private int    expiresAfterSecs;
    @JsonProperty("media_key")
    private String mediaKey;
    private int    size;
  }
}
