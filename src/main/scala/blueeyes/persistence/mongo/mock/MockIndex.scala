package blueeyes.persistence.mongo.mock

import blueeyes.json.JPath
import com.mongodb.MongoException
import scala.collection.immutable.ListSet
import blueeyes.persistence.mongo.{GeospatialIndex, IndexType, MongoSelection}
import blueeyes.json.JsonAST._

private[mock] trait MockIndex extends JObjectFields{
  private var indexes   = Map[String, Tuple2[Set[(JPath, IndexType)], JObject]]()

  def ensureIndex(name: String, keys: ListSet[(JPath, IndexType)], unique: Boolean, options: JObject){
    indexes = if (unique) indexes.get(name) match{
      case None    => indexes + Tuple2(name, (keys, options))
      case Some(x) => indexes
    } else indexes
  }

  def dropIndex(name: String) {
    indexes = indexes - name
  }

  def indexExists(name: String): Boolean = indexes.contains(name)
  def indexExists(keys: Set[JPath]): Boolean = indexes.find(keyAndValue => keys.toSet == keyAndValue._2._1.toSet.map{v: (JPath, IndexType) => v._1}) != None

  def dropIndexes() {indexes = Map[String, Tuple2[Set[(JPath, IndexType)], JObject]]()}

  def index(newObjects: List[JObject]) {
    indexes.foreach(index => {
      val selection = MongoSelection(index._2._1.map{v: (JPath, IndexType) => v._1})
      val newFields = selectExistingFields(newObjects, selection.selection)

      if (newFields.distinct.size != newFields.size) throw new MongoException("Index contraint.")

      newObjects.foreach(jObject => {
        val existing  = selectExistingFields(indexed, selection.selection)
        if ((existing filterNot (newFields contains)).size != existing.size) throw new MongoException("Index contraint.")
      })
      //checkGeospatialRange(newObjects, index._2)
    })
  }

  private def checkGeospatialRange(newObjects: List[JObject], index: (Set[(JPath, IndexType)], JObject)){
    def rangeValue(options: JObject, rangeName: String, defaultValue: Int) = options.fields.find(field => field.name == rangeName && field.value.isInstanceOf[JInt]).map(_.value.asInstanceOf[JInt].value).getOrElse(defaultValue)
    val geospatialIndexes = index._1.filter(_._2 == GeospatialIndex)
    geospatialIndexes.foreach{geoIndex =>
      val min = rangeValue(index._2, "min", -180)
      val max = rangeValue(index._2, "min", 180)
//geo values have to be numbers: { 0: "foo", 1: 40 }
      newObjects.foreach{jObject =>
        jObject.get(geoIndex._1) match{
          case JArray(JInt(x) :: JInt(y) :: xs)       =>
          case JArray(JDouble(x) :: JDouble(y) :: xs) =>
          case JArray(x :: y :: Nil)                   => throw new MongoException("geo values have to be numbers: { 0: %s, 1: %s }".format(x.toString, y.toString))
          case JArray(x :: Nil)                        => throw new MongoException("geo field only has 1 element.")
          case JObject(JField(_, JInt(x)) :: JField(_, JInt(y)) :: Nil)       =>
          case JObject(JField(_, JDouble(x)) :: JField(_, JDouble(y)) :: Nil) =>
          case JObject(x :: y :: Nil)                                         => throw new MongoException("geo values have to be numbers: { 0: %s, 1: %s }".format(x.toString, y.toString))
          case JObject(JField(_, _):: Nil)                                    => throw new MongoException("geo field only has 1 element.")
          case _ =>

        }
      }
    }
  }

  def indexed: List[JObject]
}
