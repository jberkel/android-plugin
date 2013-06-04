package sbtandroid

import java.io.{File,FileInputStream,OutputStreamWriter,PrintWriter}
import java.net.{URL, HttpURLConnection}

import scala.util.parsing.json.JSON
import scala.xml.Node
import sbt._
import Keys._

import AndroidKeys._

object Github {
  val gitConfig = new File(System.getenv("HOME"), ".gitconfig")
  val apkMime = "application/vnd.android.package-archive"
  val gitDownloads = "https://api.github.com/repos/%s/downloads"

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    uploadGithub <<= (prepareMarket, githubRepo, cachePasswords, streams) map { (path, repo, cache, s) =>
      val (user, password) = credentials(cache)
      upload(Upload(path, "", apkMime), user, password, repo, s)
    },
    deleteGithub <<= (packageAlignedPath, githubRepo, cachePasswords, streams) map { (path, repo, cache, s) =>
      val (user, password) = credentials(cache)
      delete(path.getName, user, password, repo, s)
    },
    githubRepo := "repo"
  ))

  def repoUrl(user: String, repo: String) = {
    if (repo.contains("/"))
          gitDownloads.format(repo) else
          gitDownloads.format(user+"/"+repo)
  }

  private def credentials(cache: Boolean) = {
    val user = github_user.getOrElse(sys.error("could not get github user - add [user] to "+
                                            gitConfig.getAbsolutePath))

    val password = PasswordManager.get("github", user, cache)
                                  .getOrElse(sys.error("could not get password"))
    (user, password)
  }

  private def getGitConfig(key: String):Option[String] = {
    if (gitConfig.exists)
      """(?s).*\s*%s\s*=\s*(\w+).*""".format(key).r.findFirstMatchIn(IO.read(gitConfig)).map(_.group(1).trim)
    else
      None
  }
  private def github_user  = getGitConfig("user")
  private def github_token = getGitConfig("token")

  private def upload(upload: Upload, user: String, password: String,
                     repo: String, s: TaskStreams): Option[String] = {
    val post = Post(repoUrl(user, repo), user, password)

    s.log.info("uploading to "+post.getURL)
    post.setRequestProperty("Content-Type", "application/json")

    val fw = new OutputStreamWriter(post.getOutputStream())
    fw.write(upload.toJSON)
    fw.flush()
    fw.close()
    post.getResponseCode match {
       case 201 =>
         JSON.parseFull(IO.readStream(post.getInputStream())) match {
           case Some(data) =>
              s3_upload(upload, data.asInstanceOf[Map[String,Any]], s) map { (resp) =>
                s.log.debug("s3: received "+resp)
                s.log.success("Uploaded to s3/github")
                (resp \\ "Location").text
              }
           case _ => None
         }
       case code =>
         s.log.error("error (%d): %s".format(code, IO.readStream(post.getErrorStream())))
         None
    }
  }


  private def delete(name: String, user: String, password: String, repo: String, s: TaskStreams) = {
    val downloads = Get(repoUrl(user,repo), user, password)
    s.log.debug("GET "+downloads.getURL)

    downloads.getResponseCode match {
      case 200 =>
        JSON.parseFull(IO.readStream(downloads.getInputStream)).map { (data) =>
          data.asInstanceOf[List[Map[String,Any]]]
              .find( e => e("name") == name) map { (item) =>
            val id = item("id").asInstanceOf[Double].toInt
            val url = "%s/%d".format(repoUrl(user, repo), id)
            s.log.debug("DELETE "+url)
            val delete = Delete(url, user, password)
            delete.getResponseCode() match {
              case 204  =>
                s.log.success("deleted "+name)
              case code =>
                s.log.error("deletion failed (%d): %s"
                            .format(code,
                              if (delete.getErrorStream != null)
                              IO.readStream(delete.getErrorStream) else ""))
            }
          }
        }
      case code   =>
        s.log.error("unexpected status %d: %s".format(code,
                                              IO.readStream(downloads.getErrorStream)))
    }
  }

  private def s3_upload(upload: Upload, data: Map[String,Any], s: TaskStreams):Option[Node] = {
    val s3_url = data("s3_url").toString
    val s3_post = Post(s3_url, null, null)

    s.log.debug("uploading to "+s3_url)
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
      case 201  => Some(scala.xml.XML.load(s3_post.getInputStream()))
      case code =>
        s.log.error("unexpected status code %d: %s ".format(
            code, IO.readStream(s3_post.getErrorStream)))
        None
    }
  }

  trait HttpMethod {
    def setAuth(conn: HttpURLConnection, user: String, password: String) {
      if (user != null && password != null) {
        conn.setRequestProperty("Authorization", "Basic "+
          (new sun.misc.BASE64Encoder().encode("%s:%s".format(user, password).getBytes)))
      }
    }
  }

  object Get extends HttpMethod {
    def apply(url: String, user: String, password: String):HttpURLConnection = {
        val _url = new URL(url)
        val get = _url.openConnection().asInstanceOf[HttpURLConnection]
        get.setRequestMethod("GET")
        setAuth(get, user, password)
        get
    }
  }

  object Post extends HttpMethod {
    val CHARSET = "UTF-8"
    val CRLF = "\r\n"

    def apply(url: String, user: String, password: String):HttpURLConnection = {
      val _url = new URL(url)
      val post = _url.openConnection().asInstanceOf[HttpURLConnection]
      post.setRequestMethod("POST")
      post.setDoOutput(true)
      post.setDoInput(true)
      setAuth(post, user, password)
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
          .append(upload.f.getName).append('"').append(CRLF)
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

  object Delete extends HttpMethod  {
    def apply(url: String, user: String, password: String):HttpURLConnection = {
        val _url = new URL(url)
        val delete = _url.openConnection().asInstanceOf[HttpURLConnection]
        delete.setRequestMethod("DELETE")
        delete.setDoOutput(false)
        setAuth(delete, user, password)
        delete
    }
  }

  case class Upload(f: File, description:String, contentType:String) {
    def toJSON = """{
        "name": "%s",
        "size": %d,
        "description": "%s",
        "content_type": "%s"
    }""".format(f.getName, f.length(), description, contentType)
  }
}
