package org.mrgeo.org.mrgeo.hdfs

import java.io.{Externalizable, ObjectOutput, ObjectInput}

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark.rdd.RDD
import org.mrgeo.data.raster.RasterWritable
import org.mrgeo.data.tile.TileIdWritable
import org.mrgeo.hdfs.image.HdfsMrsImageDataProvider
import org.mrgeo.hdfs.partitioners.SplitGenerator
import org.mrgeo.hdfs.tile.{FileSplit, PartitionerSplit}
import org.mrgeo.spark.SparkTileIdPartitioner
import org.mrgeo.utils.SparkUtils

@SerialVersionUID(-1)
class HdfsSparkTileIdPartitioner(splitGenerator: SplitGenerator)
  extends SparkTileIdPartitioner with Externalizable
{
  private val splits = new PartitionerSplit

  if (splitGenerator != null) {
    splits.generateSplits(splitGenerator)
  }

  def this() {
    this(null)
  }

  override def numPartitions: Int = {
    splits.length
  }

  override def getPartition(key: Any): Int = {
    key match {
      case id: TileIdWritable =>
        val split = splits.getSplit(id.get())
        split.getPartition
      case _ => throw new RuntimeException("Bad type sent into SparkTileIdPartitioner.getPartition(): " +
        key.getClass + ". Expected org.mrgeo.data.tile.TileIdWritable or a subclass.")
    }
  }

  def generateFileSplits(rdd:RDD[(TileIdWritable, RasterWritable)], pyramid:String, zoom:Int, conf:Configuration) = {
    val fileSplits = new FileSplit

    val splitinfo = SparkUtils.calculateSplitData(rdd)
    fileSplits.generateSplits(splitinfo)

    val dp: HdfsMrsImageDataProvider = new HdfsMrsImageDataProvider(conf, pyramid, null)
    val inputWithZoom = new Path(dp.getResourcePath(false), "" + zoom)

    fileSplits.writeSplits(inputWithZoom)
  }

  override def readExternal(in: ObjectInput): Unit = {
    splits.readExternal(in)
  }

  override def writeExternal(out: ObjectOutput): Unit = {
    splits.writeExternal(out)
  }
}