package collapse

import scala.collection.mutable

/**
  * generates a concensus read from aggregate base-counts
  * @param stringLen the length of the sequences
  */
class SequenceCounter(stringLen: Int) {

  // our counts per string index, per base (in that order)
  val counts = Array.ofDim[Int](stringLen,SequenceCounter.knownBases.size)
  val baseCount = stringLen
  var readCount = 0

  /**
    * add a read to our aggregate collection sequence counts
    * @param seqs
    */
  def addSequence(sq: String): Unit = {
    assert(sq.length <= baseCount,"The sequence container isnt long enough: " + sq.length + " for container size " + baseCount)
    sq.toUpperCase().zipWithIndex.foreach{case(base,index) => {
      counts(index)(SequenceCounter.baseToIndex(base)) += 1
    }}
    readCount += 1
  }

  /**
    * given our aggregate base-counts, collapse out a sequence into a string of the most likely bases
    * @param minBaseCallRate the minimum proportion a single base has to be to call a position
    * @param maxNProportion the maximum proportion of Ns we'll tolerate before returning NONE
    * @return a SequenceCounterResult with a string representing the collapsed read,
    *         wrapped in an Option, or NONE if we couldn't call the sequence. Also some stats about the process
    */
  def countsToSequence(minBaseCallRate: Double, maxNProportion: Double, minPerBase:Int=1, trimTerminalNs: Boolean = true): SequenceCounterResult = {
    val returnString = new mutable.ArrayBuffer[Char]()

    (0 until baseCount).foreach{case(index) => {
      returnString += SequenceCounter.pileupToCall(counts(index), minBaseCallRate)
    }}

    val finalString = returnString.toArray.mkString("")
    val nProp = finalString.map{base => if(base == 'N') 1.0 else 0.0}.sum / finalString.size.toDouble

    if (nProp < maxNProportion) {
      val fString = if (trimTerminalNs) finalString.replaceAll("[N]+$", "") else finalString
      SequenceCounterResult(Some(fString), readCount, nProp)
    } else
      SequenceCounterResult(None,readCount,nProp)
  }
}

object SequenceCounter {

  // N should always be the last base here
  val knownBases: Array[Char] = Array[Char]('A','C','G','T','N')
  val unknownBase = knownBases(knownBases.size - 1)

  def baseToIndex(base: Char): Int = base match {
    case x if  x == SequenceCounter.knownBases(0) => 0
    case x if  x == SequenceCounter.knownBases(1) => 1
    case x if  x == SequenceCounter.knownBases(2) => 2
    case x if  x == SequenceCounter.knownBases(3) => 3
    case _ => knownBases.size - 1
  }

  /**
    * given an array of base-counts, choose the top base as long as the best_count/(total_count) is above
    * the minimum call threshold
    * @param counts the array of counts
    * @param minCallRate our minimum proportion of one base to call the position
    * @return the called base, N if there's too much (> minCallRate) confusion at the base
    */
  def pileupToCall(counts: Array[Int], minCallRate: Double): Char = {
    assert(counts.size == knownBases.size, "Arrays passed to pileupToCall must be of length " + knownBases.size)

    val max = counts.max
    val maxIndex = counts.indexOf(max)
    val total = counts.sum
    val topProportion = max.toDouble / total.toDouble
    if (topProportion >= minCallRate)
      knownBases(maxIndex)
    else
      unknownBase
  }

}


case class SequenceCounterResult(string: Option[String], count: Int, nProp: Double)