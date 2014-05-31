package org.iainhull.resttest

import org.scalatest.Matchers.AnyShouldWrapper
import org.scalatest.matchers.HavePropertyMatcher
import org.scalatest.matchers.HavePropertyMatchResult
import org.scalatest.Assertions
import scala.util.Success

/**
 * Adds [[http://www.scalatest.org/ ScalaTest]] support to the RestTest [[Dsl]].
 *
 * The `should` keyword is added to [[Api.RequestBuilder]] and [[Api.Response]] expressions, the
 * `RequestBuilder` is executed first and `should` applied to the `Response`.
 *
 * The `have` keyword supports [[Extractors.ExtractorLike]]s.  See [[ExtractorToHavePropertyMatcher]] for more details.
 *
 * === Example ===
 *
 * {{{
 * using (_ url "http://api.rest.org/person") { implicit rb =>
 *   GET should have (statusCode(Status.OK), jsonBodyAsList[Person] === EmptyList)
 *
 *   val (status, id) = POST body personJson returning (statusCode, headerText("X-Person-Id"))
 *   status should be(Status.Created)
 *
 *   val foo = GET / id should have (statusCode(Status.OK), jsonBodyAs[Person] === Jason)
 *
 *   GET should have (statusCode === Status.OK, jsonBodyAsList[Person] === Seq(Jason))
 *
 *   DELETE / id should have (statusCode === Status.OK)
 *
 *   GET / id should have (statusCode === Status.NotFound)
 *
 *   GET should have (statusCode(Status.OK), jsonBodyAsList[Person] === EmptyList)
 * }
 * }}}
 */
trait RestMatchers {
  import language.implicitConversions
  import Api._
  import Dsl._

  /**
   * Implicitly execute a [[Api.RequestBuilder]] and convert the [[Api.Response]] into a `AnyRefShouldWrapper`
   *
   * This adds support for ScalaTest's `ShouldMatchers` to `RequestBuilder`
   */
  implicit def requestBuilderToShouldWrapper(builder: RequestBuilder)(implicit client: HttpClient): AnyShouldWrapper[Response] = {
    responseToShouldWrapper(builder execute ())
  }

  /**
   * Implicitly convert a [[Api.Response]] into a `AnyRefShouldWrapper`
   *
   * This adds support for ScalaTest's `ShouldMatchers` to `Response`
   */
  implicit def responseToShouldWrapper(response: Response): AnyShouldWrapper[Response] = {
    new AnyShouldWrapper(response)
  }

  implicit def methodToShouldWrapper(method: Method)(implicit builder: RequestBuilder, client: HttpClient): AnyShouldWrapper[Response] = {
    requestBuilderToShouldWrapper(builder.withMethod(method))
  }

  implicit def extractorToShouldWrapper[T](extractor: ExtractorLike[T])(implicit response: Response): AnyShouldWrapper[T] = {
    Assertions.withClue(extractor.name) {
      val v: T = extractor.value.get
      new AnyShouldWrapper[T](v)
    }
  }  
  
  /**
   * Implicitly add operations to [[Extractors.Extractor]] that create `HavePropertyMatcher`s.
   *
   * This adds support for reusing `Extractor`s in `should have(...)` expressions, for example
   *
   * {{{
   * response should have(statusCode(Status.OK))
   * }}}
   */
  implicit class ExtractorToHavePropertyMatcher[T](extractor: ExtractorLike[T]) {
    def apply(expected: T): HavePropertyMatcher[Response, String] = {
      new HavePropertyMatcher[Response, String] {
        def apply(response: Response) = {
          val actual = extractor.value(response)
          new HavePropertyMatchResult(
            actual == Success(expected),
            extractor.name,
            expected.toString,
            actual.toString)
        }
      }
    }
  }

  /**
   * Implicitly convert an [[Extractors.Extractor]] that returns any type of `Option` into a `HavePropertyMatcher`.
   *
   * This adds support for reusing `Extractor[Option[_]]`s in `should have(...)` expressions, for example
   *
   * {{{
   * response should have(header("header2"))
   * response should have(body)
   * }}}
   */
  implicit class OptionExtractorToHavePropertyMatcher(extractor: ExtractorLike[Option[_]]) extends HavePropertyMatcher[Response, String] {
    def apply(response: Response) = {
      val actual = extractor.value(response)
      val isDefined = actual.map(a => a.isDefined).getOrElse(false)
      new HavePropertyMatchResult(
        isDefined,
        extractor.name,
        "defined",
        (if (isDefined) "" else "not") + " defined")
    }
  }
}

object RestMatchers extends RestMatchers