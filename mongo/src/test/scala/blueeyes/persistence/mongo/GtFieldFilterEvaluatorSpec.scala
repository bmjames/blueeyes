package blueeyes.persistence.mongo

import org.specs2.mutable.Specification
import blueeyes.json.JsonAST._
import Evaluators._


class GtFieldFilterEvaluatorSpec extends Specification {
  "returns true when one string greater then another string" in {
    GtFieldFilterEvaluator(JString("b"), JString("a")) must be_==(true)
  }
  "returns false when one string less then another string" in {
    GtFieldFilterEvaluator(JString("a"), JString("b")) must be_==(false)
  }
  "returns true when one number greater then another number" in {
    GtFieldFilterEvaluator(JNum(2), JNum(1)) must be_==(true)
  }
  "returns false when one number less then another number" in {
    GtFieldFilterEvaluator(JNum(1), JNum(2)) must be_==(false)
  }
  "returns true when one double greater then another double" in {
    GtFieldFilterEvaluator(JNum(2.2), JNum(1.1)) must be_==(true)
  }
  "returns false when one double less then another double" in {
    GtFieldFilterEvaluator(JNum(1.1), JNum(2.2)) must be_==(false)
  }
  "returns true when one boolean greater then another boolean" in {
    GtFieldFilterEvaluator(JBool(true), JBool(false)) must be_==(true)
  }
  "returns false when one boolean less then another boolean" in {
    GtFieldFilterEvaluator(JBool(false), JBool(true)) must be_==(false)
  }
  "returns false when different object are compared" in {
    GtFieldFilterEvaluator(JBool(false), JNum(1)) must be_==(false)
  }
  "returns false when objecta are the same" in {
    GtFieldFilterEvaluator(JNum(1), JNum(1)) must be_==(false)
  }
  
}