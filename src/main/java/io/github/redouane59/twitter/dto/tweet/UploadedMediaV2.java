package io.github.redouane59.twitter.dto.tweet;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Objects;


@Data
public final class UploadedMediaV2 {

    private int imageWidth;
    private int imageHeight;
    private String imageType;
    private String id;
    private long size;
    private String processingState;
    private Integer processingCheckAfterSecs;
    private Integer progressPercent;

    // 失败信息字段
    private String detail;
    private String type;
    private String title;
    private String status;
    private String error;
    private String errors;

    public UploadedMediaV2(JSONObject json) throws Exception {
        init(json);
    }

    private void init(JSONObject data) throws Exception {
        JSONObject json = data.getJSONObject("data");
        detail = data.getString("detail");
        type = data.getString("type");
        title = data.getString("title");
        status = data.getString("status");
        error = data.getString("error");
        errors = data.getString("errors");

        try {
            id = json.getString("id");
            size = Objects.isNull(json.getLong("size")) ? 0 : json.getLong("size");
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

        UploadedMediaV2 that = (UploadedMediaV2) o;

        if (imageWidth != that.imageWidth) {
            return false;
        }
        if (imageHeight != that.imageHeight) {
            return false;
        }
        if (!Objects.equals(imageType, that.imageType)) {
            return false;
        }
        if (id != that.id) {
            return false;
        }
        return size == that.size;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0; // 使用 String 的 hashCode 方法
        result = 31 * result + imageWidth;
        result = 31 * result + imageHeight;
        result = 31 * result + (imageType != null ? imageType.hashCode() : 0);
        result = 31 * result + Long.hashCode(size); // 使用 Long.hashCode 替代位移操作
        return result;
    }

    @Override
    public String toString() {
        return "UploadedMedia{" +
                "id=" + id +
                ", imageWidth=" + imageWidth +
                ", imageHeight=" + imageHeight +
                ", imageType='" + imageType + '\'' +
                ", size=" + size +
                '}';
    }
}
