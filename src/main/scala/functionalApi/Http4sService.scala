package functionalApi

import cats._
import cats.effect._
import cats.implicits._
import org.http4s.circe._
import org.http4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.typelevel.ci.CIString

import java.time.Year
import java.util.UUID
import scala.collection.mutable
import scala.util.Try

object Http4sService extends IOApp {

  case class Movie(id: String, title: String, year: Int, actors: List[String], director: String)
  case class Director(name: String, surname: String) {
    override def toString: String = s"$name $surname"
  }
  case class DirectorDetails(name: String, surname: String, genre: String)


  // DB
  val snjl: Movie = Movie(
    "6bcbca1e-efd3-411d-9f7c-14b872444fce",
    "Zack Snyder's Justice League",
    2021,
    List("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa"),
    "Zack Snyder"
  )

  val movies: Map[String, Movie] = Map(snjl.id -> snjl)

  private def findMovieById(movieId: UUID) =
    movies.get(movieId.toString)

  private def findMoviesByDirector(director: String): List[Movie] =
    movies.values.filter(_.director == director).toList

  // DB end

  object DirectorQueryParameterMatcher extends  QueryParamDecoderMatcher[String]("director")
  object YearQueryParamMatcher extends  OptionalValidatingQueryParamDecoderMatcher[Year]("year")

  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
    QueryParamDecoder[Int].emap { yearint =>
      Try(Year.of(yearint))
        .toEither
        .leftMap { e =>
          ParseFailure(e.getMessage, e.getMessage)
        }
    }

  def movieRoutes[F[_] : Monad]: HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "movies" :? DirectorQueryParameterMatcher(director) +& YearQueryParamMatcher(maybeYear) =>
        val moviesByDirector = findMoviesByDirector(director)
        maybeYear match {
          case Some(year) =>
            year.fold (
              _ => BadRequest(s"The year was badly formated."),

              validYear => {
                val moviesByDirectorAndYear = moviesByDirector.filter(_.year == validYear.getValue)
                Ok(moviesByDirectorAndYear.asJson)
              }
            )
          case None => Ok(moviesByDirector.asJson)

        }
      case GET -> Root / "movies" / UUIDVar(movieId) / "actors" =>
        findMovieById(movieId).map(_.actors) match {
          case Some(actors) => Ok(actors.asJson)
          case _ => NotFound(s"No movie with id $movieId found")

        }
      }
  }

  object DirectorPath {
    def unapply(str: String): Option[Director] = {
      Try {
        val tokens = str.split(" ")
        Director(tokens(0), tokens(1))
      }.toOption
    }
  }

  val directorsDB: mutable.Map[Director, DirectorDetails] =
    mutable.Map[Director, DirectorDetails](Director("Ray", "Standers") -> DirectorDetails("Ray", "Standers", "fantasy"))

  def directorRoutes[F[_] : Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "directors" / DirectorPath(director) =>
        directorsDB.get(director) match {
          case Some(directorDetails) => Ok(directorDetails.asJson)
          case _ => NotFound(s"No $director found")

        }
    }
  }

  def allRoutes[F[_] : Monad]: HttpRoutes[F] =
    movieRoutes[F] <+> directorRoutes[F]

  def allRoutesComplete[F[_] : Monad]: HttpApp[F] =
    allRoutes[F].orNotFound // 404



  override def run(args: List[String]): IO[ExitCode] = {

    val api = Router (
      "/api" -> movieRoutes[IO],
      "/api/admin" -> directorRoutes[IO]
    ).orNotFound

    BlazeServerBuilder[IO](runtime.compute)
      .bindHttp(8080, "localhost")
      .withHttpApp(allRoutesComplete)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

}
