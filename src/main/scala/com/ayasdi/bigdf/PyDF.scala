/* Ayasdi Inc. Copyright 2015 - all rights reserved. */
/**
 * @author abhishek, ajith, mohit
 *         big dataframe on spark: wrappers for python access via py4j
 */
package com.ayasdi.bigdf

import java.util.{ArrayList => JArrayList}
import java.util.{HashMap => JHashMap}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.reflect._
import scala.reflect.runtime.{universe => ru}

import org.apache.spark.api.java.JavaRDD
import org.apache.spark.rdd.RDD
import org.apache.spark.{BigDFPyRDD, SparkContext}
import com.ayasdi.bigdf.Implicits._
import org.apache.spark.sql.{Column => SColumn}

case class PyDF(df: DF) {
  def columnNames = df.columnNames

  def column(name: String) = PyColumn(df.column(name))

  def expandMapColumn(name: String, namePrefix: String) =
    df.column(name).expand(df, null, namePrefix)
  
  def columnByIndex(index: Int) = PyColumn(df.column(index))  

  def deleteColumn(colName: String) = df.delete(colName)

  def colCount = df.columnCount

  def rowCount = df.rowCount
  
  // def rowsByRange(start: Int, end: Int) = {
  //   val r = Range(start, end)
  //   PyDF(df.rowsByRange(r))
  // }

  def rename(columns: JHashMap[String, String], inPlace: Boolean) =
    df.rename(columns.toMap, inPlace)

  def list(numRows: Int, numCols: Int) = df.list(numRows, numCols)

  def where(predicate: PyPredicate): PyDF = {
    PyDF(df.where(predicate.p))
  }

  def update(name: String, pycol: PyColumn[_]): Unit = {
    df.update(name, pycol.col)
  }

  def compareSchema(a: PyDF, b: PyDF) =
    DF.compareSchema(a.df, b.df)

  def join(sc: SparkContext, left: PyDF, right: PyDF, on: String, how: String) = {
    PyDF(left.df.join(right.df, on, how))
  }

  def aggregate(byColumnJ: JArrayList[String], aggrColumnJ: JArrayList[String], aggregator: String): PyDF = {
    val byColumn = byColumnJ.asScala.toList
    val aggrColumn = aggrColumnJ.asScala.toList
    val aggMap = Map(aggrColumn.head -> aggregator)
    val dfAgg = aggregator match {
      case "Mean" => df.aggregate(byColumn, aggMap)
      case "Sum" => df.aggregate(byColumn, aggMap)
      case "Count" => {
        df(aggrColumn.head).colType match {
          case ColType.Double => df.aggregate(byColumn, aggMap)
          case ColType.String => df.aggregate(byColumn, aggMap)
          case _ => throw new IllegalArgumentException("Count not yet supported for this column type")
        }
      }
      case "StrJoin" => throw new IllegalArgumentException("not supported yet")
      case _ => null
    }
    PyDF(dfAgg)
  }

  def select(colNames: JArrayList[String])  = df.select(colNames.head, colNames.tail : _ *)

  def groupBy(colName: String) = df.groupBy(colName)

  def pivot(keyCol: String, pivotByCol: String,
            pivotedCols: JArrayList[String]): PyDF = {
   // PyDF(df.pivot(keyCol, pivotByCol, pivotedCols.asScala.toList))
    this
  }

  def writeToCSV(file: String, separator: String, singlePart: Boolean,
    cols: JArrayList[String]): Unit =
    df.writeToCSV(file, separator, singlePart, cols.asScala.toList)

  def writeToParquet(file: String, cols: JArrayList[String]): Unit =
    df.writeToParquet(file, cols.asScala.toList)

}

object PyDF {
  def fromCSV(sc: SparkContext, name: String, separator: String, fasterGuess: Boolean, nParts: Int): PyDF = {
    val sep: Char = separator.charAt(0)
    PyDF(DF(sc, name, sep, nParts, Options()))
  }

  def fromCSVDir(sc: SparkContext, name: String, pattern: String, recursive: Boolean, separator: String) =
    PyDF(DF.fromCSVDir(sc, name, pattern, recursive, separator.charAt(0), 0, Options()))

  def readParquet(sc: SparkContext, infile: String): PyDF = {
    PyDF(DF.fromParquet(sc, infile, Options()))
  }
}

case class PyColumn[T: ru.TypeTag](col: Column) {
  def list(numRows: Int) = col.list(numRows)
  def head(numRows: Int) = col.head(numRows)
  def count = col.count

  def mean = col.mean()
  def max = col.doubleRdd.max()
  def min = col.doubleRdd.max()
  def stddev = col.stdev()
  def variance = col.variance()
  def histogram(nBuckets: Int) = col.histogram(nBuckets)
  def first = col.getRdd[T].first()
  def distinct = col.getRdd[T].distinct()
  def sum = col.sum()
  def stats = col.stats()

  override def toString = {
    val name = s"${col.name}".split('/').last.split('.').head
    s"$name\t${col.colType}"
  }

  def name = col.name
  def tpe = s"${col.colType}"

//  def javaToPython: JavaRDD[Array[Byte]] = col.colType match {
//    case ColType.Double => BigDFPyRDD.pythonRDD(col.doubleRdd)
//    case ColType.String => BigDFPyRDD.pythonRDD(col.stringRdd)
//    case ColType.Float => BigDFPyRDD.pythonRDD(col.floatRdd)
//    case ColType.ArrayOfDouble => BigDFPyRDD.pythonRDD(col.arrayOfDoubleRdd)
//    case ColType.ArrayOfString => BigDFPyRDD.pythonRDD(col.arrayOfStringRdd)
//    case ColType.Short => BigDFPyRDD.pythonRDD(col.shortRdd)
//    case ColType.Long => BigDFPyRDD.pythonRDD(col.longRdd)
//    case ColType.MapOfStringToFloat => BigDFPyRDD.pythonRDD(col.mapOfStringToFloatRdd)
//    case ColType.Undefined => throw new IllegalArgumentException("Undefined column type")
//  }
//
//  def pythonToJava[T: ClassTag](c: JavaRDD[Array[Byte]]): PyColumn[Any] = {
//    //FIXME: other types
//    val jrdd: JavaRDD[T] = BigDFPyRDD.javaRDD(c)
//    val tpe = classTag[T]
//    if (tpe == classTag[Double]) PyColumn[Double](Column(jrdd.rdd.asInstanceOf[RDD[Double]]))
//    else if (tpe == classTag[String]) PyColumn(Column(jrdd.rdd.asInstanceOf[RDD[String]]))
//    else null
//  }

  def add(v: Double) = {
    PyColumn(col + v)
  }

  def sub(v: Double) = {
    PyColumn(col - v)
  }

  def mul(v: Double) = {
    PyColumn(col * v)
  }

  def div(v: Double) = {
    PyColumn(col / v)
  }

}

object PyColumn {
  def fromRDD[T: ClassTag](rdd: RDD[T]) = {
    //FIXME: other types
    val tpe = classTag[T]
    if (tpe == classTag[Double]) PyColumn(Column(rdd.asInstanceOf[RDD[Double]]))
    else if (tpe == classTag[String]) PyColumn(Column(rdd.asInstanceOf[RDD[String]]))
    else null
  }
}


case class PyPredicate(p: SColumn) {
  def And(that: PyPredicate) = {
    PyPredicate(this.p && that.p)
  }

  def Or(that: PyPredicate) = {
    PyPredicate(this.p || that.p)
  }

  def Not() = {
    PyPredicate(!this.p)
  }
}

object PyPredicate {
  def where[T](column: PyColumn[T], operator: String, value: Double): PyPredicate = {
    val filter = operator match {
      case "==" => column.col === value
      case "!=" => column.col !== value
      case "<" => column.col < value
      case "<=" => column.col <= value
      case ">" => column.col > value
      case ">=" => column.col >= value
    }
    PyPredicate(filter)
  }

  def where[T](column: PyColumn[T], operator: String, value: String): PyPredicate = {
    val filter = operator match {
      case "==" => column.col === value
      case "!=" => column.col !== value
      case "<" => column.col < value
      case "<=" => column.col <= value
      case ">" => column.col > value
      case ">=" => column.col >= value
    }
    PyPredicate(filter)
  }
}

object ClassTagUtil {
  val double = classTag[Double]
  val string = classTag[String]
}

object TypeTagUtil {
  val double = ru.typeTag[Double]
  val string = ru.typeTag[String]
}

object OrderingUtil {
  val double = scala.math.Ordering.Double
  val string = scala.math.Ordering.String  
}
