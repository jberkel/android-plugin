import java.io.{File,FileInputStream,OutputStreamWriter,PrintWriter,BufferedReader,InputStreamReader}
import java.net.{URL, HttpURLConnection}

import scala.util.parsing.json.JSON
import scala.xml.Node
import sbt._
import Keys._

import AndroidKeys._

object Github {
  val uploadGithub = TaskKey[Option[String]]("github-upload", "Upload file to github")
  val githubRepo   = SettingKey[String]("github-repo", "Github repo")

  val gitConfig = new File(System.getenv("HOME"), ".gitconfig")
  val apkMime = "application/vnd.android.package-archive"
  val githubPassword = "GITHUB_PASSWORD"

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    uploadGithub <<= (prepareMarket, githubRepo, streams) map { (path, repo, s) =>
      val user = github_user.getOrElse(error("could not get github user - add [user] to %s"
                                             .format(gitConfig.getAbsolutePath)))
      val password = github_password.getOrElse(error("could not get password - set "+
                                               githubPassword))

      upload(Upload(path, "", apkMime), user, password, repo, s)
    },
    githubRepo := "repo"
  ))


  private def getGitConfig(key: String):Option[String] = {
    if (gitConfig.exists)
      """(?s).*\s*%s\s*=\s*(\w+).*""".format(key).r.findFirstMatchIn(IO.read(gitConfig)).map(_.group(1).trim)
    else
      None
  }
  private def github_user  = getGitConfig("user")
  private def github_token = getGitConfig("token")
  private def github_password = {
    val password = System.getenv(githubPassword)
    if (password != null) Some(password) else None
  }

  private def upload(upload: Upload, user: String, password: String,
                     repo: String, s: TaskStreams): Option[String] = {
    val post = Post("https://api.github.com/repos/%s/%s/downloads".format(user,repo))
    post.setRequestProperty("Authorization", "Basic "+
      (new sun.misc.BASE64Encoder().encode("%s:%s".format(user, password).getBytes)))
    post.setRequestProperty("Content-Type", "application/json")

    val fw = new OutputStreamWriter(post.getOutputStream())
    fw.write(upload.toJSON)
    fw.flush
    fw.close
    post.getResponseCode match {
       case 201 =>
         JSON.parseFull(IO.readStream(post.getInputStream())) match {
           case Some(data) =>
              s3_upload(upload, data.asInstanceOf[Map[String,Any]], s) map { (resp) =>
                s.log.debug("s3: received "+resp)
                (resp \\ "Location").text
              }
           case _ => None
         }
       case _ =>
         s.log.error("error (%d): %s".format(post.getResponseCode,
                     IO.readStream(post.getErrorStream())))
         None
    }
  }

  private def s3_upload(upload: Upload, data: Map[String,Any], s: TaskStreams):Option[Node] = {
    val s3_url = data("s3_url").toString
    val s3_post = Post(s3_url)

    s.log.debug("posting to "+s3_url)
    Post.multipart(s3_post, upload, Seq( /* order matters for signature */
        ("key"           , data("path")),
        ("acl"           , data("acl")),
        ("success_action_status", "201"),
        ("Filename"      , data("name")),
        ("AWSAccessKeyId", data("accesskeyid")),
        ("Policy"        , data("policy")),
        ("Signature"     , data("signature")),
        ("Content-Type"  , data("mime_type"))
    )).getResponseCode match {
      case 201 => Some(scala.xml.XML.load(s3_post.getInputStream()))
      case _   =>
        s.log.error("unexpected status code %d: %s ".format(s3_post.getResponseCode,
            IO.readStream(s3_post.getErrorStream)))
        None
    }
  }

  object Post {
    val CHARSET = "UTF-8"
    val CRLF = "\r\n"

    def apply(url: String):HttpURLConnection = {
      val _url = new URL(url)
      val post = _url.openConnection().asInstanceOf[HttpURLConnection]
      post.setRequestMethod("POST")
      post.setDoOutput(true)
      post.setDoInput(true)
      post
    }

    def multipart(conn: HttpURLConnection, upload: Upload, params: Seq[(String,Any)]) = {
      val boundary = java.lang.Long.toHexString(System.currentTimeMillis())
      conn.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary)
      var writer:PrintWriter = null
      try {
        val output = conn.getOutputStream()
        writer = new PrintWriter(new OutputStreamWriter(output, CHARSET), true)
        for ((name,value) <- params)
          writer.append("--").append(boundary).append(CRLF)
            .append("Content-Disposition: form-data; name=\"").append(name).append('"').append(CRLF)
            .append(CRLF)
            .append(value.toString)
            .append(CRLF)

        writer.flush()
        writer.append("--").append(boundary).append(CRLF)
          .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
          .append(upload.f.getName()).append('"').append(CRLF)
          .append("Content-Type: ").append(upload.contentType).append(CRLF)
          .append("Content-Transfer-Encoding: binary").append(CRLF)
          .append(CRLF).flush()

        IO.transferAndClose(new FileInputStream(upload.f), output)

        output.flush()
        writer.append(CRLF).flush()
        writer.append("--" + boundary + "--").append(CRLF);
      } finally {
        if (writer != null) writer.close()
      }
      conn
    }
  }

  case class Upload(val f: File, val description:String, val contentType:String) {
    def toJSON() = """{
        "name": "%s",
        "size": %d,
        "description": "%s",
        "content_type": "%s"
    }""".format(f.getName(), f.length(), description, contentType)
  }
}
