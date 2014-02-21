package org.scalablitz



import scala.collection._
import org.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.Gen._
import Conc._



object ConcChecks extends Properties("Conc") with ConcSnippets {

  /* conc tree */

  val genLeaf = for (n <- choose(0, 500)) yield new Conc.Single(n)

  def genTree(level: Int): Gen[Conc[Int]] = if (level <= 0) genLeaf else for {
    tp <- oneOf(0, 1, 2)
    left <- if (tp == 0) genTree(level - 2) else genTree(level - 1)
    right <- if (tp == 2) genTree(level - 2) else genTree(level - 1)
  } yield new <>(left, right)

  def trees(maxlevel: Int) = for {
    level <- choose(0, maxlevel + 1)
    tree <- genTree(level)
  } yield tree

  property("<> correctness") = forAll(choose(0, 500), choose(0, 500)) {
    testConcatCorrectness
  }

  property("<> balance") = forAll(choose(0, 500), choose(0, 500)) {
    testConcatBalance
  }

  property("apply correctness") = forAll(choose(1, 500)) {
    testApply
  }

  property("update correctness") = forAll(choose(1, 500)) {
    testUpdate
  }

  property("insert correctness") = forAll(choose(0, 500), choose(0, 20), choose(0, 500)) {
    testInsert
  }

  property("generated trees") = forAll(trees(10)) { tree =>
    s"invariants: $tree" |: checkInvs(tree)
  }

  property("left shake") = forAll(trees(10)) { tree =>
    val shaken = Conc.shakeLeft(tree)
    all(
      s"invariants: $shaken" |: checkInvs(shaken),
      s"leaning left: $shaken" |: (shaken.level <= 1 || shaken.level < tree.level || shaken.left.level >= shaken.right.level)
    )
  }

  property("right shake") = forAll(trees(10)) { tree =>
    val shaken = Conc.shakeRight(tree)
    all(
      s"invariants: $shaken" |: checkInvs(shaken),
      s"leaning right: $shaken" |: (shaken.level <= 1 || shaken.level < tree.level || shaken.left.level <= shaken.right.level)
    )
  }

  /* conc rope */

  property("append correctness") = forAll(choose(1, 1000), choose(1, 5000)) {
    testAppendCorrectness
  }

  property("append balance") = forAll(choose(1, 1000), choose(1, 5000)) {
    testAppendBalance
  }

  /* conqueue */

  def genSequence[T](length: Int, g: Gen[T]): Gen[Seq[T]] = for {
    head <- g
    tail <- if (length <= 1) oneOf(Nil, Nil) else genSequence(length - 1, g)
  } yield head +: tail

  def genNum(num: Int, rank: Int) = for {
    xs <- genSequence(num, genTree(rank))
  } yield xs.length  match {
    case 0 => Zero
    case 1 => One(xs(0))
    case 2 => Two(xs(0), xs(1))
    case 3 => Three(xs(0), xs(1), xs(2))
    case 4 => Four(xs(0), xs(1), xs(2), xs(3))
  }

  def genTip(rank: Int) = for {
    num <- oneOf(2, 3)
    xs <- genNum(num, rank)
  } yield Tip(xs)

  def genSpine(rank: Int, maxRank: Int): Gen[Spine[Int]] = for {
    leftNum <- oneOf(2, 3)
    rightNum <- oneOf(2, 3)
    leftSide <- genNum(leftNum, rank)
    rightSide <- genNum(rightNum, rank)
    tail <- genConqueue(rank + 1, maxRank)
  } yield Spine(null, leftSide, tail, rightSide, null)

  def genConqueue(rank: Int, maxRank: Int) = for {
    conqueue <- if (rank == maxRank) genTip(rank) else genSpine(rank, maxRank)
  } yield conqueue

  def queues(rankLimit: Int) = for {
    maxRank <- choose(0, rankLimit)
    conqueue <- genConqueue(0, maxRank)
  } yield conqueue

  property("head correctness") = forAll(queues(2)) { conq =>
    val buffer = mutable.Buffer[Int]()
    for (x <- conq) buffer += x
    buffer.head == head(conq).asInstanceOf[Single[Int]].x
  }

  property("last correctness") = forAll(queues(2)) { conq =>
    val buffer = mutable.Buffer[Int]()
    for (x <- conq) buffer += x
    s"${queueString(conq)}\n: ${buffer.last} vs ${last(conq)}" |: buffer.last == last(conq).asInstanceOf[Single[Int]].x
  }

}






