package com.github.redouane59.twitter.nrt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.redouane59.RelationType;
import com.github.redouane59.twitter.TwitterClient;
import com.github.redouane59.twitter.dto.others.RequestToken;
import com.github.redouane59.twitter.dto.tweet.MediaCategory;
import com.github.redouane59.twitter.dto.tweet.Tweet;
import com.github.redouane59.twitter.dto.tweet.UploadMediaResponse;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class ITwitterClientV1Test {

  private static TwitterClient twitterClient;

  @BeforeAll
  public static void init() {
    twitterClient = new TwitterClient();
  }

  @Test
  public void testFriendshipByIdYes() {
    String       userId1 = "92073489";
    String       userId2 = "723996356";
    RelationType result  = twitterClient.getRelationType(userId1, userId2);
    assertEquals(RelationType.FRIENDS, result);
  }

  @Test
  public void testFriendshipByIdNo() {
    String       userId1 = "92073489";
    String       userId2 = "1976143068";
    RelationType result  = twitterClient.getRelationType(userId1, userId2);
    assertNotEquals(RelationType.FRIENDS, result);
  }

  @Test
  public void testGetRateLimitStatus() {
    assertNotEquals(null, twitterClient.getRateLimitStatus());
  }

  @Test
  public void testRelationBetweenUsersIdFriends() {
    String       userId1 = "92073489";
    String       userId2 = "723996356";
    RelationType result  = twitterClient.getRelationType(userId1, userId2);
    assertEquals(RelationType.FRIENDS, result);
  }

  @Test
  public void testRelationBetweenUsersIdNone() {
    String       userId1 = "92073489";
    String       userId2 = "1976143068";
    RelationType result  = twitterClient.getRelationType(userId1, userId2);
    assertEquals(RelationType.NONE, result);
  }

  @Test
  public void testRelationBetweenUsersIdFollowing() {
    String       userId1 = "92073489";
    String       userId2 = "126267113";
    RelationType result  = twitterClient.getRelationType(userId1, userId2);
    assertEquals(RelationType.FOLLOWING, result);
  }

  @Test
  public void testRelationBetweenUsersIdFollower() {
    String       userId1 = "92073489";
    String       userId2 = "1218125226095054848";
    RelationType result  = twitterClient.getRelationType(userId1, userId2);
    assertEquals(RelationType.FOLLOWER, result);
  }

  @Test
  public void testGetRetweetersId() {
    String tweetId = "1078358350000205824";
    assertTrue(twitterClient.getRetweetersId(tweetId).size() > 10);
  }

  @Test
  public void testGetOauth1Token() {
    twitterClient.getTwitterCredentials().setAccessToken("");
    twitterClient.getTwitterCredentials().setAccessTokenSecret("");
    RequestToken result = twitterClient.getOauth1Token("oob");
    assertTrue(result.getOauthToken().length() > 1);
    assertTrue(result.getOauthTokenSecret().length() > 1);
    //twitterClient.getOAuth1AccessToken(result, "12345");
  }

  @Test
  public void testPostAndRTandDeleteTweet() {
    String text       = "API Test " + LocalDateTime.now() + " #TwitterAPI";
    Tweet  resultPost = twitterClient.postTweet(text);
    assertNotNull(resultPost);
    assertNotNull(resultPost.getId());
    assertEquals(text, resultPost.getText());
    Tweet resultPostAnswer = twitterClient.postTweet(text, resultPost.getId());
    assertNotNull(resultPostAnswer);
    assertNotNull(resultPostAnswer.getId());
    assertEquals(resultPostAnswer.getInReplyToStatusId(), resultPost.getId());
    Tweet resultRT = twitterClient.retweetTweet(resultPost.getId());
    assertNotNull(resultRT);
    assertNotNull(resultRT.getId());
    assertEquals(resultPost.getAuthorId(), resultRT.getAuthorId());
    Tweet resultDelete = twitterClient.deleteTweet(resultPost.getId());
    assertNotNull(resultDelete);
    assertEquals(resultPost.getId(), resultDelete.getId());
    Tweet resultDelete2 = twitterClient.deleteTweet(resultPostAnswer.getId());
    assertNotNull(resultDelete2);
    assertEquals(resultPostAnswer.getId(), resultDelete2.getId());
  }

  @Test
  public void testGetFavorites() {
    int         count     = 1500;
    List<Tweet> favorites = twitterClient.getFavorites("92073489", count);
    assertNotNull(favorites);
    assertTrue(favorites.size() > count);
  }

  @Test
  public void testSearchTweets30days() {
    List<Tweet>
        result =
        twitterClient.searchForTweetsWithin30days("@RedTheOne -RT", LocalDateTime.of(2020, 9, 1, 0, 0), LocalDateTime.of(2020, 9, 3, 0, 0), "30days");
    assertTrue(result.size() > 0);
  }

  @Test
  public void testUploadPngWithByeArray() throws Exception {

    try (InputStream is = ITwitterClientV1Test.class.getResourceAsStream("/twitter.png");
         ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
      byte[] buf = new byte[1024];
      int    k;
      while ((k = is.read(buf)) > 0) {
        baos.write(buf, 0, k);
      }
      UploadMediaResponse response = twitterClient.uploadMedia("twitter.png", baos.toByteArray(), MediaCategory.TWEET_IMAGE);
      assertNotNull(response);
      assertNotNull(response.getMediaId());
      Tweet tweet = twitterClient.postTweet("Test", null, response.getMediaId());
      assertNotNull(tweet);
      assertNotNull(tweet.getId());
      twitterClient.deleteTweet(tweet.getId());
    }
  }

  @Test
  public void testUploadPngFile() {
    File                img      = new File(getClass().getClassLoader().getResource("twitter.png").getFile());
    UploadMediaResponse response = twitterClient.uploadMedia(img, MediaCategory.TWEET_GIF);
    assertNotNull(response);
    assertNotNull(response.getMediaId());
    Tweet tweet = twitterClient.postTweet("Test", null, response.getMediaId());
    assertNotNull(tweet);
    assertNotNull(tweet.getId());
    twitterClient.deleteTweet(tweet.getId());
  }

  @Test
  public void testUploadGif() throws IOException {
    File          gif = new File(getClass().getClassLoader().getResource("zen.gif").getFile());
    StringBuilder sb  = new StringBuilder();
    try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(gif))) {
      for (int b; (b = is.read()) != -1; ) {
        String s = Integer.toHexString(b).toUpperCase();
        if (s.length() == 1) {
          sb.append('0');
        }
        sb.append(s).append(' ');
      }
    }
    String              initUrl  = twitterClient.getUrlHelper().getUploadMediaInitUrl(MediaCategory.TWEET_GIF, gif.length());
    UploadMediaResponse response = twitterClient.getRequestHelperV1().postRequest(initUrl, new HashMap<>(), UploadMediaResponse.class).get();
    assertNotNull(response.getMediaId());
    String appendUrl = twitterClient.getUrlHelper().getUploadMediaAppendUrl(response.getMediaId(), 0, sb.toString());
    // to be complete for APPEND call
    String finalizeUrl = twitterClient.getUrlHelper().getUploadMediaFinalizeUrl(response.getMediaId());
    // to be complete for FINALIZE call
  }

  @Test
  public void testAnswerToSeveralUsers() {
    Tweet tweet = twitterClient.postTweet(".", "1369395732415922182");
    assertNotNull(tweet);
    assertNotNull(tweet.getId());
    twitterClient.deleteTweet(tweet.getId());
  }

  /*

    @Test
    public void testSearchTweetsArchive(){
        LocalDateTime startDate = DateUtils.truncate(ConverterHelper.dayBeforeNow(60),Calendar.MONTH);
        LocalDateTime endDate = DateUtils.addDays(startDate, 1);
        List<ITweet> result = twitterClient.searchForTweetsArchive("@RedTheOne -RT",startDate, endDate);
        assertTrue(result.size()>0);
    } */

}