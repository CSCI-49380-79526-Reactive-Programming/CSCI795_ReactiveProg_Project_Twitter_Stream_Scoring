package tweet
import java.io.File

import com.github.tototoshi.csv._

import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken, Tweet}

class TwitterSecrets(filePath_ : String) {
  private val csvData = CSVReader.open(new File(filePath_)).all()
  private val consumerKey       = csvData(1)(0)
  private val consumerSecret    = csvData(1)(1)
  private val accessTokenKey    = csvData(1)(2)
  private val accessTokenSecret = csvData(1)(3)

  private val consumerToken = ConsumerToken(
    key     = consumerKey,
    secret  = consumerSecret
  )
  private val accessToken = AccessToken(
    key     = accessTokenKey,
    secret  = accessTokenSecret
  )

  def getTokens() : (ConsumerToken, AccessToken) = {
    return (consumerToken, accessToken)
  }

}

// A Twitter score object.
// Intended to be associated with a politician.
// Internally keeps a mutable state of Tweets received as either disputed or undisputed.
// Can provide a "Pinochiocco Score" based on the current internal state.
class TwitterScoring(wordRanking_ : Map[String,Int], tweet_ : Tweet) {
  // Two sets of tweet IDs for Pinocchio score
  private val disputed   = new scala.collection.mutable.HashSet[Long]
  private val undisputed = new scala.collection.mutable.HashSet[Long]

  // Two sets of tweet IDs for Positivity score
  private val wordRanks  = wordRanking_
  private val positive   = new scala.collection.mutable.HashSet[Long]
  private val negative   = new scala.collection.mutable.HashSet[Long]
  private val neutral    = new scala.collection.mutable.HashSet[Long]

  updatePositivityScore(tweet_)
  updatePinocchioScore(tweet_)

  def flagged(tweet : Tweet) : Boolean = {
    !tweet.withheld_in_countries.isEmpty
  }

  def update(tweet : Tweet) : (Option[Double], Option[Double]) = {
    val x = updatePositivityScore(tweet)
    val y = updatePinocchioScore(tweet)
    (x, y)
  }

  def updatePinocchioScore(tweet : Tweet) : Option[Double] = {
    val id = tweet.id
    if (disputed contains id) {
      // We already processed this tweet as disputed
      if (!flagged(tweet)) {
        // However it's status has changed to undisputed
        disputed.remove(id)
        undisputed.add(id)
        return Some(pinocchioScore)
       }
       // And no change is required
    }
    else if (undisputed contains id) {
      // We processed this tweet as undisputed,
      if (flagged(tweet)) {
        // However it's status has changed to disputed
        disputed.add(id)
        undisputed.remove(id)
        return Some(pinocchioScore)
      }
      // And no change is required
    }
    else {
      // We have *not* processed this tweet
      if (flagged(tweet)) {
        disputed.add(id)
      }
      else {
        undisputed.add(id)
      }
      return Some(pinocchioScore)
    }
    None
  }

  def updatePositivityScore(tweet : Tweet) : Option[Double] = {
    val id = tweet.id

    if ((positive contains id) || (neutral contains id) || (negative contains id)) {
      // We already processed this tweet
      return None
    }

    // Otherwise analyse and process the tweet
    val score = analyzeText(tweet.text)
    if (score == 0) {
      neutral.add(id)
    }
    else if (score > 0) {
      positive.add(id)
    }
    else {
      negative.add(id)
    }
    Some(positivityScore)
  }

  def pinocchioScore() : Double = {
    disputed.size.toDouble / (disputed.size + undisputed.size).toDouble
  }
  
  def positivityScore() : Double = {
    (positive.size - negative.size).toDouble / (positive.size + neutral.size + negative.size).toDouble
  }

  def analyzeText(text : String) : Int = {
    // Get rid of punctuation
    // Make every character lower-case
    // Split on whitespace
    val cleanedStream = text.replaceAll("""[\p{Punct}&&[^.]]""", "")
                            .toLowerCase()
                            .split("\\ +")

    cleanedStream.foldLeft(0) { (x : Int, word : String) =>
      wordRanks.get(word) match {
        case Some(v) => x + v
        case None    => x
      }
    }
  }

}