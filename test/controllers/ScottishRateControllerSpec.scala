/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import forms.ScottishRateFormProvider
import models.{QuickCalcAggregateInput, ScottishRate, UserTaxCode}
import org.jsoup.Jsoup
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Tag, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.QuickCalcCache
import setup.QuickCalcCacheSetup._
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.HeaderCarrierConverter
import views.html.pages.ScottishRateView

import scala.concurrent.Future

class ScottishRateControllerSpec
    extends PlaySpec
    with TryValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar {

  val formProvider = new ScottishRateFormProvider()
  val form         = formProvider()

  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("", "").withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

  def messagesThing(app: Application): Messages =
    app.injector.instanceOf[MessagesApi].preferred(fakeRequest)

  "The show Scottish rate page" should {
    "return 200 - OK with existing aggregate data" in {
      val mockCache = mock[QuickCalcCache]

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        cacheCompleteYearly
      )

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .build()

      implicit val messages: Messages = messagesThing(application)

      val formFilled =
        form.fill(cacheCompleteYearly.value.savedScottishRate.get)
      running(application) {

        val request = FakeRequest(
          GET,
          routes.ScottishRateController.showScottishRateForm().url
        ).withHeaders(HeaderNames.xSessionId -> "test").withCSRFToken

        val result = route(application, request).value

        val view = application.injector.instanceOf[ScottishRateView]

        status(result) mustEqual OK

        contentAsString(result) mustEqual view(
          formFilled,
          cacheCompleteYearly.value.youHaveToldUsItems
        )(request, messagesThing(application)).toString
        verify(mockCache, times(1)).fetchAndGetEntry()(any())

      }
    }

    "return 303 See Other and redirect to the salary page with no aggregate data" in {
      val mockCache = mock[QuickCalcCache]

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val request = FakeRequest(
          GET,
          routes.ScottishRateController.showScottishRateForm().url
        ).withHeaders(HeaderNames.xSessionId -> "test").withCSRFToken

        val result = route(application, request).value

        val view = application.injector.instanceOf[ScottishRateView]

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.SalaryController
          .showSalaryForm()
          .url
        verify(mockCache, times(1)).fetchAndGetEntry()(any())

      }

    }
  }
  "The submit Scottish rate page" should {
    "return 400 Bad Request if the user does not select whether they pay the Scottish rate or not" in {
      val mockCache = mock[QuickCalcCache]

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        cacheCompleteYearly
      )

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .build()
      implicit val messages: Messages = messagesThing(application)
      running(application) {

        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST

        val parseHtml = Jsoup.parse(contentAsString(result))

        val errorHeader      = parseHtml.getElementById("error-summary-title").text()
        val errorMessageLink = parseHtml.getElementsByClass("govuk-list govuk-error-summary__list").text()
        val errorMessage     = parseHtml.getElementsByClass("govuk-error-message").text()

        errorHeader mustEqual "There is a problem"
        errorMessageLink.contains(expectedInvalidScottishRateAnswer) mustEqual true
        errorMessage.contains(expectedInvalidScottishRateAnswer) mustEqual true
      }
    }

    "return 303 See Other and redirect to the Check Your Answers page if they submit valid form data" in {
      val mockCache = mock[QuickCalcCache]

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "true")

        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        val view = application.injector.instanceOf[ScottishRateView]

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.YouHaveToldUsController.summary().url
        verify(mockCache, times(1)).fetchAndGetEntry()(any())
      }
    }

    "set the user's tax code to the 2018-19 default UK tax code if the user does not pay the Scottish rate" taggedAs Tag(
      "2018"
    ) in {
      val expectedAggregate: QuickCalcAggregateInput =
        cacheCompleteYearly.get.copy(
          savedScottishRate = Some(ScottishRate(false)),
          savedTaxCode      = Some(UserTaxCode(gaveUsTaxCode = false, Some("1185L")))
        )

      val mockCache = mock[QuickCalcCache]

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(
        fakeRequest.headers,
        Some(fakeRequest.session)
      )

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      when(mockCache.save(expectedAggregate)(hc)) thenReturn Future
        .successful(CacheMap("id", Map.empty))

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .configure("dateOverride" -> "2018-04-06")
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "false")
        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "set the user's tax code to the 2018-19 default Scottish tax code if the user pays the Scottish rate" taggedAs Tag(
      "2018"
    ) in {
      val expectedAggregate: QuickCalcAggregateInput =
        cacheCompleteYearly.get.copy(
          savedScottishRate = Some(ScottishRate(true)),
          savedTaxCode      = Some(UserTaxCode(gaveUsTaxCode = false, Some("S1185L")))
        )

      val mockCache = mock[QuickCalcCache]

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(
        fakeRequest.headers,
        Some(fakeRequest.session)
      )

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      when(mockCache.save(expectedAggregate)(hc)) thenReturn Future
        .successful(CacheMap("id", Map.empty))

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .configure("dateOverride" -> "2018-04-06")
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "true")
        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "set the user's tax code to the 2019-20 default UK tax code if the user does not pay the Scottish rate" taggedAs Tag(
      "2019"
    ) in {
      val expectedAggregate: QuickCalcAggregateInput =
        cacheCompleteYearly.get.copy(
          savedScottishRate = Some(ScottishRate(false)),
          savedTaxCode      = Some(UserTaxCode(gaveUsTaxCode = false, Some("1250L")))
        )

      val mockCache = mock[QuickCalcCache]

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(
        fakeRequest.headers,
        Some(fakeRequest.session)
      )

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      when(mockCache.save(expectedAggregate)(hc)) thenReturn Future
        .successful(CacheMap("id", Map.empty))

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .configure("dateOverride" -> "2019-04-06")
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "false")
        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "set the user's tax code to the 2019-20 default Scottish tax code if the user pays the Scottish rate" taggedAs Tag(
      "2019"
    ) in {
      val expectedAggregate: QuickCalcAggregateInput =
        cacheCompleteYearly.get.copy(
          savedScottishRate = Some(ScottishRate(true)),
          savedTaxCode      = Some(UserTaxCode(gaveUsTaxCode = false, Some("S1250L")))
        )

      val mockCache = mock[QuickCalcCache]

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(
        fakeRequest.headers,
        Some(fakeRequest.session)
      )

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      when(mockCache.save(expectedAggregate)(hc)) thenReturn Future
        .successful(CacheMap("id", Map.empty))

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .configure("dateOverride" -> "2019-04-06")
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "true")
        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "set the user's tax code to the 2020-21 default UK tax code if the user does not pay the Scottish rate" taggedAs Tag(
      "2020"
    ) in {
      val expectedAggregate: QuickCalcAggregateInput =
        cacheCompleteYearly.get.copy(
          savedScottishRate = Some(ScottishRate(false)),
          savedTaxCode      = Some(UserTaxCode(gaveUsTaxCode = false, Some("1250L")))
        )

      val mockCache = mock[QuickCalcCache]

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(
        fakeRequest.headers,
        Some(fakeRequest.session)
      )

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      when(mockCache.save(expectedAggregate)(hc)) thenReturn Future
        .successful(CacheMap("id", Map.empty))

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .configure("dateOverride" -> "2020-04-06")
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "false")
        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "set the user's tax code to the 2020-21 default Scottish tax code if the user pays the Scottish rate" taggedAs Tag(
      "2020"
    ) in {
      val expectedAggregate: QuickCalcAggregateInput =
        cacheCompleteYearly.get.copy(
          savedScottishRate = Some(ScottishRate(true)),
          savedTaxCode      = Some(UserTaxCode(gaveUsTaxCode = false, Some("S1250L")))
        )

      val mockCache = mock[QuickCalcCache]

      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(
        fakeRequest.headers,
        Some(fakeRequest.session)
      )

      when(mockCache.fetchAndGetEntry()(any())) thenReturn Future.successful(
        None
      )

      when(mockCache.save(expectedAggregate)(hc)) thenReturn Future
        .successful(CacheMap("id", Map.empty))

      val application = new GuiceApplicationBuilder()
        .overrides(bind[QuickCalcCache].toInstance(mockCache))
        .configure("dateOverride" -> "2020-04-06")
        .build()

      implicit val messages: Messages = messagesThing(application)

      running(application) {

        val formData = Map("payScottishRate" -> "true")
        val request = FakeRequest(
          POST,
          routes.ScottishRateController.submitScottishRateForm().url
        ).withFormUrlEncodedBody(form.bind(formData).data.toSeq: _*)
          .withHeaders(HeaderNames.xSessionId -> "test")
          .withCSRFToken

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }
  }

}
