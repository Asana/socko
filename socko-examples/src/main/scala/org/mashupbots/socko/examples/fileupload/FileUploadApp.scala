//
// Copyright 2012 Vibul Imtarnasan, David Bolton and Socko contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.socko.examples.fileupload

import java.io.File
import java.io.FileOutputStream

import org.jboss.netty.util.CharsetUtil
import org.mashupbots.socko.context.HttpRequestProcessingContext
import org.mashupbots.socko.processors.StaticFileProcessor
import org.mashupbots.socko.processors.StaticFileRequest
import org.mashupbots.socko.routes.GET
import org.mashupbots.socko.routes.POST
import org.mashupbots.socko.routes.Path
import org.mashupbots.socko.routes.PathSegments
import org.mashupbots.socko.routes.Routes
import org.mashupbots.socko.utils.Logger
import org.mashupbots.socko.webserver.WebServer
import org.mashupbots.socko.webserver.WebServerConfig

import com.typesafe.config.ConfigFactory

import akka.actor.actorRef2Scala
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.FromConfig

/**
 * This example shows how use [[org.mashupbots.socko.processors.StaticFileProcessor]] to download files and
 * [[org.mashupbots.socko.postdecoder.HttpPostRequestDecoder]] to process file uploads.
 *  - Run this class as a Scala Application
 *  - Open your browser and navigate to `http://localhost:8888`.
 */
object FileUploadApp extends Logger {

  val contentDir = createTempDir("content_")
  val tempDir = createTempDir("temp_")

  //
  // STEP #1 - Define Actors and Start Akka
  //
  // We are going to start StaticFileProcessor actor as a router.
  // There will be 5 instances, each instance having its own thread since there is a lot of blocking IO.
  //
  // FileUploadProcessor will also be started as a router with a PinnedDispatcher since it involves IO.
  //
  val actorConfig = "my-pinned-dispatcher {\n" +
    "  type=PinnedDispatcher\n" +
    "  executor=thread-pool-executor\n" +
    "}\n" +
    "akka {\n" +
    "  event-handlers = [\"akka.event.slf4j.Slf4jEventHandler\"]\n" +
    "  loglevel=DEBUG\n" +
    "  actor {\n" +
    "    deployment {\n" +
    "      /static-file-router {\n" +
    "        router = round-robin\n" +
    "        nr-of-instances = 5\n" +
    "      }\n" +
    "      /file-upload-router {\n" +
    "        router = round-robin\n" +
    "        nr-of-instances = 5\n" +
    "      }\n" +
    "    }\n" +
    "  }\n" +
    "}"

  val actorSystem = ActorSystem("FileUploadExampleActorSystem", ConfigFactory.parseString(actorConfig))

  val staticFileProcessorRouter = actorSystem.actorOf(Props[StaticFileProcessor]
    .withRouter(FromConfig()).withDispatcher("my-pinned-dispatcher"), "static-file-router")

  val fileUploadProcessorRouter = actorSystem.actorOf(Props[FileUploadProcessor]
    .withRouter(FromConfig()).withDispatcher("my-pinned-dispatcher"), "file-upload-router")

  //
  // STEP #2 - Define Routes
  //
  val routes = Routes({
    case ctx @ GET(Path("/")) => {
      // Redirect to index.html
      // This is a quick non-blocking operation so executing it in the netty thread pool is OK. 
      val httpContext = ctx.asInstanceOf[HttpRequestProcessingContext]
      val endPoint = httpContext.endPoint
      httpContext.redirect("http://localhost:8888/index.html")
    }
    case ctx @ GET(Path(PathSegments(fileName :: Nil))) => {
      // download requested file
      val request = new StaticFileRequest(
        ctx.asInstanceOf[HttpRequestProcessingContext],
        contentDir,
        new File(contentDir, fileName),
        tempDir)
      staticFileProcessorRouter ! request
    }
    case ctx @ POST(Path("/upload")) => {
      // save file to the content directory so it can be downloaded
      val request = FileUploadRequest(ctx.asInstanceOf[HttpRequestProcessingContext], contentDir)
      fileUploadProcessorRouter ! request
    }
  })

  //
  // STEP #3 - Start and Stop Socko Web Server
  //
  def main(args: Array[String]) {
    // Create content
    createContent(contentDir)

    // Start web server
    val webServer = new WebServer(WebServerConfig(), routes)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run {
        webServer.stop()
        contentDir.delete()
        tempDir.delete()
      }
    })
    webServer.start()

    System.out.println("Open your browser and navigate to http://localhost:8888")
  }

  /**
   * Returns a newly created temp directory
   *
   * @param namePrefix Prefix to use on the directory name
   * @returns Newly created directory
   */
  private def createTempDir(namePrefix: String): File = {
    val d = File.createTempFile(namePrefix, "")
    d.delete()
    d.mkdir()
    d
  }

  /**
   * Delete the specified directory and all sub directories
   *
   * @param dir Directory to delete
   */
  private def deleteTempDir(dir: File) {
    if (dir.exists()) {
      val files = dir.listFiles()
      files.foreach(f => {
        if (f.isFile) {
          f.delete()
        } else {
          deleteTempDir(dir)
        }
      })
    }
    dir.delete()
  }

  /**
   * Creates html and css files in the specified directory
   */
  private def createContent(dir: File) {
    val buf = new StringBuilder()
    buf.append("<html>\n")
    buf.append("<head>\n")
    buf.append("  <title>Socko File Upload Example</title>\n")
    buf.append("  <link rel=\"stylesheet\" type=\"text/css\" href=\"mystyle.css\" />\n")
    buf.append("</head>\n")
    buf.append("<body>\n")
    buf.append("<h1>Socko File Upload Example</h1>\n")
    buf.append("<form action=\"/upload\" enctype=\"multipart/form-data\" method=\"post\">\n")

    buf.append("  <div class=\"field\">\n")
    buf.append("    <label>1. Select a file to upload</label><br/>\n")
    buf.append("    <input type=\"file\" name=\"fileUpload\" />\n")
    buf.append("  </div>\n")

    buf.append("  <div class=\"field\">\n")
    buf.append("    <label>2. Description</label><br/>\n")
    buf.append("    <input type=\"text\" name=\"fileDescription\" size=\"50\" />\n")
    buf.append("  </div>\n")

    buf.append("  <div class=\"field\">\n")
    buf.append("    <input type=\"submit\" value=\"Upload\" />\n")
    buf.append("  </div>\n")

    buf.append("</form>\n")
    buf.append("</body>\n")
    buf.append("</html>\n")

    val indexFile = new File(dir, "index.html")
    val out = new FileOutputStream(indexFile)
    out.write(buf.toString.getBytes(CharsetUtil.UTF_8))
    out.close()
    
    buf.setLength(0)
    buf.append("body { font-family: Arial,Helv,Courier,Serif}\n")
    buf.append("div.field {margin-top:20px;}\n")
    
    val cssFile = new File(dir, "mystyle.css")
    val out2 = new FileOutputStream(cssFile)
    out2.write(buf.toString.getBytes(CharsetUtil.UTF_8))
    out2.close()

  }
}