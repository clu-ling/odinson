package ai.lum.odinson.extra

import java.io._
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

import ai.lum.common.ConfigFactory
import com.typesafe.config.{Config, ConfigValueFactory}

import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{Document, OdinsonIndexWriter}

import scala.collection.GenIterable

object IndexDocuments extends App with LazyLogging {
  //
  var config = ConfigFactory.load()
  // inject a new arg for the folder
  if (args.length == 1) {
    logger.info(s"Received dataDir as a parameter <${args(0)}>")
    // receive the path from the arguments
    config = config
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(args(0)))
      // re-compute the index and docs path's
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(args(0) + "/index")
      )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(args(0) + "/docs")
      )
  }
  //
  val docsDir = config[File]("odinson.docsDir")
  val synchronizeOrderWithDocumentId =
    config[Boolean]("odinson.index.synchronizeOrderWithDocumentId")
  //
  val writer = OdinsonIndexWriter.fromConfig(config.getConfig("odinson"))
  val wildcards = Seq("*.json", "*.json.gz")
  logger.info(s"Gathering documents from $docsDir")
  // make this a function
  val documentFiles =
    if (synchronizeOrderWithDocumentId) {
      // files ordered by the id of the document
      docsDir
        .listFilesByWildcards(wildcards, recursive = true)
        .map(f => (Document.fromJson(f).id.toInt, f))
        .toSeq
        .sortBy(_._1)
        .map(_._2)
    } else {
      docsDir
        .listFilesByWildcards(wildcards, recursive = true)
        .par
    }
  // ^ this part should be a function
  logger.info("Indexing documents")
  indexDocuments(writer, documentFiles)
  writer.close
  // fin
  // Note that documentFiles may or may not be parallel, hence the GenIterable
  def indexDocuments(
      writer: OdinsonIndexWriter,
      documentFiles: GenIterable[File]
  ): Unit = {
    // index documents
    for (f <- documentFiles) {
      Try {
        writer.addFile(f, storeName = true)
      } match {
        case Success(_) =>
          logger.info(s"Indexed ${f.getName}")
        case Failure(e) =>
          logger.error(s"Failed to index ${f.getName}", e)
      }
    }
  }
}
