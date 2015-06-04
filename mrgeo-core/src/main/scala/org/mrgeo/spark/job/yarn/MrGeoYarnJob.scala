package org.mrgeo.spark.job.yarn

import java.io.{InputStreamReader, BufferedReader, FileReader}

import org.apache.hadoop.fs.Path
import org.apache.spark.{Logging, SparkConf, SparkContext}
import org.mrgeo.hdfs.utils.HadoopFileUtils
import org.mrgeo.spark.job.{JobArguments, MrGeoJob}
import org.mrgeo.utils.SparkUtils
import sun.tools.jar.resources.jar

object MrGeoYarnJob extends Logging {

  def main(args:Array[String]): Unit = {
    logInfo("Running a MrGeoYarnJob!")

    logInfo("Job Arguments: ")
    args.foreach(p => logInfo("   " + p))

    val job: JobArguments = new JobArguments(args)

    // if we have an argfile, read the parameters and put them into the job
    if (job.hasSetting(MrGeoYarnDriver.ARGFILE))
    {
      val filename = new Path(job.getSetting(MrGeoYarnDriver.ARGFILE))

      val stream = HadoopFileUtils.open(filename)
      val input = new BufferedReader(new InputStreamReader(stream))

      var key:String = ""
      var value:String = ""

      key = input.readLine()
      value = input.readLine()

      while (key != null && value != null) {
        key = key.replaceFirst("^--", "") // strip initial "--"
        if (value.startsWith("--")) {
          // The key is an on/off switch because the value is not really
          // a value, so continue parsing with the value

          job.setSetting(key, null)
          key = value.replaceFirst("^--", "") // strip initial "--"
          value = input.readLine()
          while (value != null && value.startsWith("--")) {
            job.setSetting(key, null)
            key = value.replaceFirst("^--", "") // strip initial "--"
            value = input.readLine()
          }
        }
        else {
          job.setSetting(key, value)
        }

        key = input.readLine()
        value = input.readLine()
      }

      if (key != null) {
        job.setSetting(key, null)
      }

      job.params -= MrGeoYarnDriver.ARGFILE

      logInfo("*******************")
      logInfo("Arguments")
      job.params.foreach(kv => {logInfo("  " + kv._1 + ": " + kv._2)})
      logInfo("*******************")

      input.close()
      HadoopFileUtils.delete(filename)

    }

    if (job.params.contains(MrGeoYarnDriver.DRIVER)) {
      val driver: String = job.params.getOrElseUpdate(MrGeoYarnDriver.DRIVER, "")

      val clazz = getClass.getClassLoader.loadClass(driver)
      if (clazz != null) {
        logInfo("Found MrGeo driver: " + driver)
        val mrgeo: MrGeoJob = clazz.newInstance().asInstanceOf[MrGeoJob]

        // set all the spark settings back...
        val conf = SparkUtils.getConfiguration

        logInfo("Setting up job: " + job.name)
        mrgeo.setup(job, conf)


        logInfo("SparkConf parameters")
        conf.getAll.foreach(kv => {logDebug("  " + kv._1 + ": " + kv._2)})

        val context = new SparkContext(conf)

        try {
          logInfo("Running job: " + job.name)
          mrgeo.execute(context)
        }
        finally {
          logInfo("Stopping spark context")
          context.stop()
        }

        logInfo("Teardown job: " + job.name)
        mrgeo.teardown(job, conf)
      }
    }
  }
}
