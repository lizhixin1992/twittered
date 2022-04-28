package io.github.redouane59.twitter.dto.tweet;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.Objects;

/**
 * Represents result of "/1.1/media/upload.json"
 */
@Data
public final class UploadedMedia {

    private int imageWidth;
    private int imageHeight;
    private String imageType;
    private long mediaId;
    private long size;
    private String processingState;
    private Integer processingCheckAfterSecs;
    private Integer progressPercent;

    public UploadedMedia(JSONObject json) throws Exception {
        init(json);
    }

    private void init(JSONObject json) throws Exception {
        mediaId = json.getLong("media_id");
        size = Objects.isNull(json.getLong("size")) ? 0 : json.getLong("size");
        try {
            if (json.containsKey("image")) {
                JSONObject image = json.getJSONObject("image");
                imageWidth = Objects.isNull(image.getInteger("w")) ? 0 : image.getInteger("w");
                imageHeight = Objects.isNull(image.getInteger("h")) ? 0 : image.getInteger("h");
                imageType = image.getString("image_type");
            }

            if (json.containsKey("processing_info")) {
                JSONObject processingInfo = json.getJSONObject("processing_info");
                processingState = processingInfo.getString("state");
                processingCheckAfterSecs = processingInfo.getInteger("check_after_secs");
                progressPercent = processingInfo.getInteger("progress_percent");
            }

        } catch (JSONException jsone) {
            throw new Exception(jsone);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UploadedMedia that = (UploadedMedia) o;

        if (imageWidth != that.imageWidth) {
            return false;
        }
        if (imageHeight != that.imageHeight) {
            return false;
        }
        if (!Objects.equals(imageType, that.imageType)) {
            return false;
        }
        if (mediaId != that.mediaId) {
            return false;
        }
        return size == that.size;
    }

    @Override
    public int hashCode() {
        int result = (int) (mediaId ^ (mediaId >>> 32));
        result = 31 * result + imageWidth;
        result = 31 * result + imageHeight;
        result = 31 * result + (imageType != null ? imageType.hashCode() : 0);
        result = 31 * result + (int) (size ^ (size >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "UploadedMedia{" +
                "mediaId=" + mediaId +
                ", imageWidth=" + imageWidth +
                ", imageHeight=" + imageHeight +
                ", imageType='" + imageType + '\'' +
                ", size=" + size +
                '}';
    }
}
