package org.allenai.deep_qa.parse

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

import com.mattg.util.FileUtil

class QuestionInterpreterSpec extends FlatSpecLike with Matchers {
  // We'll just use this concrete interpreter to test the methods of the abstract base class.
  val interpreter = new AppendAnswerInterpreter

  "parseQuestionLine" should "split answer options from the question text when the correct answer is given" in {
    val questionLine = "B\tSentence 1. Sentence 2 ___. (A) answer 1 (B) answer 2 (C) answer 3 (D) answer 4"
    interpreter.parseQuestionLine(questionLine) should be(ScienceQuestion(
      Seq("Sentence 1.", "Sentence 2 ___."),
      Seq(
        Answer("answer 1", false),
        Answer("answer 2", true),
        Answer("answer 3", false),
        Answer("answer 4", false)
      )
    ))
  }

  it should "give all answers as false when no answer is given" in {
    val questionLine = "Sentence 1. Sentence 2 ___. (A) answer 1 (B) answer 2 (C) answer 3 (D) answer 4"
    interpreter.parseQuestionLine(questionLine) should be(ScienceQuestion(
      Seq("Sentence 1.", "Sentence 2 ___."),
      Seq(
        Answer("answer 1", false),
        Answer("answer 2", false),
        Answer("answer 3", false),
        Answer("answer 4", false)
      )
    ))
  }
}

class AppendAnswerInterpreterSpec extends FlatSpecLike with Matchers {
  val interpreter = new AppendAnswerInterpreter

  "processQuestion" should "append each answer to the question" in {
    val questionLine = "B\tSentence 1. Sentence 2 ___. (A) answer 1 (B) answer 2 (C) answer 3 (D) answer 4"
    val expected = Seq(
      "Sentence 1. Sentence 2 ___. ||| answer 1\t0",
      "Sentence 1. Sentence 2 ___. ||| answer 2\t1",
      "Sentence 1. Sentence 2 ___. ||| answer 3\t0",
      "Sentence 1. Sentence 2 ___. ||| answer 4\t0"
    )
    interpreter.processQuestion(questionLine) should be(expected)
  }
}

class FillInTheBlankInterpreterSpec extends FlatSpecLike with Matchers {
  val params: JValue = ("wh-movement" -> "matt's")
  val interpreter = new FillInTheBlankInterpreter(params)


  "parseQuestionLine" should "correctly split the question and answer, and the answer options" in {
    val questionLine = "B\tSentence 1. Sentence 2 ___. (A) answer 1 (B) answer 2 (C) answer 3 (D) answer 4"
    val question = ScienceQuestion(
      Seq("Sentence 1.", "Sentence 2 ___."),
      Seq(
        Answer("answer 1", false),
        Answer("answer 2", true),
        Answer("answer 3", false),
        Answer("answer 4", false)
      )
    )
    interpreter.parseQuestionLine(questionLine) should be(question)
  }

  "fillInAnswerOptions" should "fill in blanks" in {
    val question = ScienceQuestion(
      Seq("Sentence 1.", "Sentence 2 ___."),
      Seq(
        Answer("answer 1", false),
        Answer("answer 2", true),
        Answer("answer 3", false),
        Answer("answer 4", false)
      )
    )
    interpreter.fillInAnswerOptions(question) should be(Some(Seq(
      ("Sentence 2 answer 1.", false),
      ("Sentence 2 answer 2.", true),
      ("Sentence 2 answer 3.", false),
      ("Sentence 2 answer 4.", false)
    )))
  }

  it should "replace wh-phrases" in {
    val question = ScienceQuestion(
      Seq("Which option is the answer?"),
      Seq(
        Answer("true", false),
        Answer("false", true)
      )
    )
    interpreter.fillInAnswerOptions(question) should be(Some(Seq(
      ("The answer is true.", false),
      ("The answer is false.", true)
    )))
  }

  it should "handle \"where is\" questions" in {
    val question = ScienceQuestion(
      Seq("Where is the answer?"),
      Seq(
        Answer("the ground", false),
        Answer("the sky", true)
      )
    )
    interpreter.fillInAnswerOptions(question) should be(Some(Seq(
      ("The answer is in the ground.", false),
      ("The answer is in the sky.", true)
    )))
  }

  it should "handle \"where is\" questions with wh-movement" in {
    val questionText = "Where is most of Earth's water located?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("oceans", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("Most of Earth's water is located in oceans.", true))))
  }

  it should "not add \"in\" if the answer already has it" in {
    val question = ScienceQuestion(
      Seq("Where is the answer?"),
      Seq(
        Answer("in the ground", false),
        Answer("in the sky", true)
      )
    )
    interpreter.fillInAnswerOptions(question) should be(Some(Seq(
      ("The answer is in the ground.", false),
      ("The answer is in the sky.", true)
    )))
  }

  ignore should "handle \"how are\" questions (Stanford parser gets this wrong)" in {
    val question = ScienceQuestion(
      Seq("How are genes usually grouped inside a cell?"),
      Seq(
        Answer("By themselves", false),
        Answer("In pairs", true),
        Answer("In fours", false),
        Answer("In threes", false)
      )
    )
    interpreter.fillInAnswerOptions(question) should be(Some(Seq(
      ("Genes are usually grouped inside a cell by themselves.", false),
      ("Genes are usually grouped inside a cell in pairs.", true),
      ("Genes are usually grouped inside a cell in fours.", false),
      ("Genes are usually grouped inside a cell in threes.", false)
    )))
  }

  it should "undo wh-movement" in {
    val questionText = "What is the male part of a flower called?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("Stamen", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("The male part of a flower is called stamen.", true))))
  }

  ignore should "undo nested wh-movement (Stanford parser gets this wrong)" in {
    val questionText = "From which part of the plant does a bee get food?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("flower", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("A bee gets food from flower.", true))))
  }

  it should "handle another wh-question" in {
    val questionText = "What is the end stage in a human's life cycle?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("death", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("The end stage in a human's life cycle is death.", true))))
  }

  it should "handle agents correctly" in {
    val questionText = "What is a waste product excreted by lungs?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("carbon dioxide", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("A waste product excreted by lungs is carbon dioxide.", true))))
  }

  ignore should "undo movement correctly (Stanford gets this wrong...)" in {
    val questionText = "Which property of air does a barometer measure?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("pressure", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("A barometer does measure pressue.", true))))
  }

  it should "handle dangling prepositions" in {
    val questionText = "Where do plants get energy from to make food?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("the sun", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("Plants do get energy to make food from the sun.", true))))
  }

  it should "order adjuncts correctly" in {
    val questionText = "What is the source of energy required to begin photosynthesis?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("sunlight", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("The source of energy required to begin photosynthesis is sunlight.", true))))
  }

  ignore should "deal with extra words correctly (parser is incorrect)" in {
    val questionText = "The digestion process begins in which of the following?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("mouth", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("The digestion process begins in mouth.", true))))
  }

  it should "deal with PP attachment on the wh word and a redundant word in the answer correctly" in {
    val questionText = "Where is the pigment chlorophyll found in plants?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("in the leaves", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("The pigment chlorophyll is found in plants in the leaves.", true))))
  }

  ignore should "deal with does in question correctly (parser is incorrect)" in {
    val questionText = "From which part of the plant does a bee get food?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("flower", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("A bee does get food from flower part of the plant.", true))))
  }

  it should "move head before prepositional phrases" in {
    val questionText = "How are genes usually grouped in a cell?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("in pairs", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("Genes are usually grouped in a cell in pairs.", true))))
  }

  it should "not add \"in\" for \"where\" questions if the answer has \"from\"" in {
    val questionText = "Where do offspring get their genes?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("From their parents", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("Offspring do get their genes from their parents.", true))))
  }

  ignore should "deal with modal verbs correctly (parser is incorrect)" in {
    val questionText = "What tool should this student use?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("magnet", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("This student should use magnet.", true))))
  }

  ignore should "get the word order right (parser is incorrect)" in {
    val questionText = "Which of the following resources does an animal use for energy?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("food", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("An animal does use food for energy.", true))))
  }

  it should "deal with Why correctly" in {
    val questionText = "Why is competition important?"
    val question = ScienceQuestion(Seq(questionText), Seq(Answer("It maintains a natural balance", true)))
    interpreter.fillInAnswerOptions(question) should be(
      Some(Seq(("Competition is important because it maintains a natural balance.", true))))
  }

  it should "not care about how many underscores there are (if there are at least 3)" in {
    val base = "This would be an example of "
    val expected = Some(Seq(("This would be an example of absorption.", true)))
    interpreter.fillInAnswerOptions(
      ScienceQuestion(Seq(base + "___."), Seq(Answer("absorption", true)))) should be(expected)
    interpreter.fillInAnswerOptions(
      ScienceQuestion(Seq(base + "_____."), Seq(Answer("absorption", true)))) should be(expected)
    interpreter.fillInAnswerOptions(
      ScienceQuestion(Seq(base + "________."), Seq(Answer("absorption", true)))) should be(expected)
    interpreter.fillInAnswerOptions(
      ScienceQuestion(Seq(base + "_________."), Seq(Answer("absorption", true)))) should be(expected)
    interpreter.fillInAnswerOptions(
      ScienceQuestion(Seq(base + "__________."), Seq(Answer("absorption", true)))) should be(expected)
  }
}
