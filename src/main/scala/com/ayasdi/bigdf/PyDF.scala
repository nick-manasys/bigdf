/* Ayasdi Inc. Copyright 2015 - all rights reserved. */
/**
 * @author abhishek, ajith, mohit
 *         big dataframe on spark: wrappers for python access via py4j
 */
package com.ayasdi.bigdf

import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.reflect._
import scala.reflect.runtime.{universe => ru}

import org.apache.spark.api.java.JavaRDD
import org.apache.spark.sql.{Column => SColumn, DataFrame}
import org.apache.spark.{BigDFPyRDD, SparkContext}
import com.ayasdi.bigdf.ColType.EnumVal
import com.databricks.spark.csv.CSVParsingOpts

case class PyDF(df: DF) {
  def sdf = df.sdf

  def columnNames = df.columnNames

  def column(name: String) = PyColumn(df.column(name))

  def expandMapColumn(name: String, namePrefix: String) =
    df.column(name).expand(null, namePrefix)

  def columnByIndex(index: Int) = PyColumn(df.column(index))

  def deleteColumn(colName: String) = df.delete(colName)

  def describe(colNames: JArrayList[String]) = df.describe(colNames.asScala.toList: _*)

  def colCount = df.columnCount

  def rowCount = df.rowCount

  def rowSlice(start: Int, end: Int) = {
    PyDF(df.rowSlice(start, end))
  }

  def rename(columns: JHashMap[String, String], inPlace: Boolean) = df.rename(columns.toMap, inPlace)

  def list(numRows: Int, numCols: Int) = df.list(numRows, numCols)

  def where(predicate: PyPredicate): PyDF = PyDF(df.where(predicate.p))

  def update(name: String, pycol: PyColumn): Unit = df.update(name, pycol.col)

  def compareSchema(a: PyDF, b: PyDF) = DF.compareSchema(a.df, b.df)

  def join(sc: SparkContext, left: PyDF, right: PyDF, on: String, how: String) =
    PyDF(left.df.join(right.df, on, how))

  def aggregate(byColumnJ: JArrayList[String], aggrColumnJ: JArrayList[String], aggregator: String): PyDF = {
    val byColumn = byColumnJ.asScala.toList
    val aggrColumn = aggrColumnJ.asScala.toList
    val aggMap = Map(aggrColumn.head -> aggregator)
    val dfAgg = df.aggregate(byColumn, aggMap)
    PyDF(dfAgg)
  }

  def aggregateMultiple(byColumnJ: JArrayList[String], aggMapJ: JHashMap[String, String]): PyDF = {
    val byColumn = byColumnJ.asScala.toList
    val aggMap = aggMapJ.toMap
    PyDF(df.aggregate(byColumn, aggMap))
  }

  def aggregateMultiple(byColumnJ: JArrayList[String], aggMapJ: JList[(String, String)]): PyDF = {
    val byColumn = byColumnJ.asScala.toList
    val aggMap = aggMapJ.asScala.toList
    PyDF(df.aggregate(byColumn, aggMap))
  }

  def select(colNames: JArrayList[String]): PyDF = PyDF(df.select(colNames.head, colNames.tail: _ *))

  def groupBy(colName: String) = df.groupBy(colName)

  def pivot(keyCol: String, pivotByCol: String,
            pivotedCols: JArrayList[String]): PyDF = {
    // PyDF(df.pivot(keyCol, pivotByCol, pivotedCols.asScala.toList))
    this
  }

  def writeToCSV(file: String, separator: String, singlePart: Boolean, cols: JArrayList[String]): Unit =
    df.writeToCSV(file, separator, singlePart, cols.asScala.toList)

  def writeToParquet(file: String, cols: JArrayList[String]): Unit =
    df.writeToParquet(file, cols.asScala.toList)

}

object PyDF {
  def fromCSV(sc: SparkContext, name: String, separator: String,
    fasterGuess: Boolean, nParts: Int, cache: Boolean): PyDF =
    PyDF(DF.fromCSVFile(sc, name,
      options = Options(perfTuningOpts=PerfTuningOpts(cache),
        csvParsingOpts = CSVParsingOpts(delimiter = separator.charAt(0)))))

  def fromCSVWithSchema(sc: SparkContext, name: String, separator: String,
    fasterGuess: Boolean, nParts: Int, schema: JHashMap[String, EnumVal],
    cache: Boolean
  ): PyDF =
    PyDF(DF.fromCSVFile(sc, name,
      options = Options(perfTuningOpts=PerfTuningOpts(cache),
        csvParsingOpts = CSVParsingOpts(delimiter = separator.charAt(0), numParts = nParts)),
      schema = schema.toMap))

  def fromCSVDir(sc: SparkContext, name: String, pattern: String, recursive: Boolean, separator: String) =
    PyDF(DF.fromCSVDir(sc, name, pattern, recursive,
      options = Options(csvParsingOpts = CSVParsingOpts(delimiter = separator.charAt(0)))))

  def readParquet(sc: SparkContext, infile: String): PyDF = {
    PyDF(DF.fromParquet(sc, infile, Options()))
  }

  def fromSparkDF(sdf: DataFrame, name: String): PyDF = {
    PyDF(DF.fromSparkDataFrame(sdf, name, Options()))
  }
}

case class PyColumn(col: Column) {
  def list(numRows: Int) = col.list(numRows)

  def head(numRows: Int) = col.head(numRows)

  def count = col.count

  def mean = col.mean()

  def max = col.doubleRdd.max()

  def min = col.doubleRdd.min()

  def stddev = col.stdev()

  def variance = col.variance()

  def histogram(nBuckets: Int) = col.histogram(nBuckets)

  def first = col.first().get(0)

  def distinct = col.distinct.collect().map(_.get(0))

  def sum = col.sum()

  def stats = col.stats()

  override def toString = {
    val name = s"${col.name}".split('/').last.split('.').head
    s"$name\t${col.colType}"
  }

  def name = col.name

  def tpe = s"${col.colType}"

  def javaToPython: JavaRDD[Array[Byte]] = col.colType match {
    case ColType.Double => BigDFPyRDD.pythonRDD(col.doubleRdd)
    case ColType.String => BigDFPyRDD.pythonRDD(col.stringRdd)
    case ColType.Float => BigDFPyRDD.pythonRDD(col.floatRdd)
    case ColType.ArrayOfDouble => BigDFPyRDD.pythonRDD(col.arrayOfDoubleRdd)
    case ColType.ArrayOfString => BigDFPyRDD.pythonRDD(col.arrayOfStringRdd)
    case ColType.Short => BigDFPyRDD.pythonRDD(col.shortRdd)
    case ColType.Int => BigDFPyRDD.pythonRDD(col.intRdd)
    case ColType.Long => BigDFPyRDD.pythonRDD(col.longRdd)
    case ColType.MapOfStringToFloat => BigDFPyRDD.pythonRDD(col.mapOfStringToFloatRdd)
    case ColType.MapOfStringToLong => BigDFPyRDD.pythonRDD(col.mapOfStringToLongRdd)
    case ColType.Undefined => throw new IllegalArgumentException("Undefined column type")
  }

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

  def add(c: PyColumn) = {
    PyColumn(col + c.col)
  }

  def sub(v: Double) = {
    PyColumn(col - v)
  }

  def sub(c: PyColumn) = {
    PyColumn(col - c.col)
  }

  def mul(v: Double) = {
    PyColumn(col * v)
  }

  def mul(c: PyColumn) = {
    PyColumn(col * c.col)
  }

  def div(v: Double) = {
    PyColumn(col / v)
  }

  def div(c: PyColumn) = {
    PyColumn(col / c.col)
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
  def where(column: PyColumn, operator: String, value: Double): PyPredicate = {
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

  def where(column: PyColumn, operator: String, value: String): PyPredicate = {
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

object ColTypeUtil {
  val String = com.ayasdi.bigdf.ColType.String
  val Float = com.ayasdi.bigdf.ColType.Float
  val Long = com.ayasdi.bigdf.ColType.Long
}
