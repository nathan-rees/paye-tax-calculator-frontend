/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.payetaxcalculatorfrontend.controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.payetaxcalculatorfrontend.utils.ActionWithSessionId
import uk.gov.hmrc.payetaxcalculatorfrontend.views.html.quickcalc._
import play.api.mvc._
import uk.gov.hmrc.payetaxcalculatorfrontend.services.QuickCalcCache
import uk.gov.hmrc.payetaxcalculatorfrontend.quickmodel._
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class QuickCalcController @Inject()(override val messagesApi: MessagesApi,
                                    cache: QuickCalcCache) extends FrontendController with I18nSupport {

  def redirectToSalaryForm() = ActionWithSessionId { implicit request =>
    Redirect(routes.QuickCalcController.showSalaryForm())
  }

  def showResult() = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        if (aggregate.allQuestionsAnswered) {
          Ok(result(aggregate))
        } else {
          redirectToNotYetDonePage(aggregate)
        }
      case None => Redirect(routes.QuickCalcController.showTaxCodeForm())
    }
  }

  def showTaxCodeForm() = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        val form = aggregate.taxCode.map(UserTaxCode.form.fill).getOrElse(UserTaxCode.form)
        Ok(tax_code(form, aggregate.youHaveToldUsItems))
      case None =>
        Ok(tax_code(UserTaxCode.form, Nil))
    }
  }

  def submitTaxCodeForm() = ActionWithSessionId.async { implicit request =>
    UserTaxCode.form.bindFromRequest.fold(
      formWithErrors => cache.fetchAndGetEntry.map {
        case Some(aggregate) => BadRequest(tax_code(formWithErrors, aggregate.youHaveToldUsItems))
        case None => BadRequest(tax_code(formWithErrors, Nil))
      },
      newTaxCode => cache.fetchAndGetEntry.flatMap {
        case Some(aggregate) =>
          val updatedTaxCode = if (newTaxCode.hasTaxCode) newTaxCode else UserTaxCode(hasTaxCode = false, Some(UserTaxCode.defaultTaxCode))
          val newAggregate = aggregate.copy(taxCode = Some(updatedTaxCode))
          cache.save(newAggregate).map { _ =>
            nextPageOrSummaryIfAllQuestionsAnswered(newAggregate) {
              Redirect(routes.QuickCalcController.showScottishRateForm())
            }
          }
        case None =>
          val aggregate = QuickCalcAggregateInput.newInstance.copy(taxCode = Some(newTaxCode))
          cache.save(aggregate).map {
            _ => Redirect(routes.QuickCalcController.showScottishRateForm())
          }
      }
    )
  }



  def showSalaryForm() = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        val form = aggregate.salary.map(Salary.salaryBaseForm.fill).getOrElse(Salary.salaryBaseForm)
        Ok(salary(form, aggregate.youHaveToldUsItems))
      case None =>
        Ok(salary(Salary.salaryBaseForm, Nil))
    }
  }

  def submitSalaryAmount() = ActionWithSessionId.async { implicit request =>
    Salary.salaryBaseForm.bindFromRequest.fold(
      formWithErrors => cache.fetchAndGetEntry.map {
        case Some(aggregate) => BadRequest(salary(formWithErrors, aggregate.youHaveToldUsItems))
        case None => BadRequest(salary(formWithErrors, Nil))
      },
      salaryAmount => cache.fetchAndGetEntry.flatMap {
        case Some(aggregate) => {
          val updatedAggregate = aggregate.copy(salary = Some(salaryAmount))
          `salaryAmount`.period match {
            case "daily" =>
              cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showDaysAWeek(Salary.salaryInPence(salaryAmount.value)))}
            case "hourly" =>
              cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showHoursAWeek(Salary.salaryInPence(salaryAmount.value)))}
            case _ =>
              cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showTaxCodeForm())}
          }
        }
        case None => cache.save(QuickCalcAggregateInput.newInstance.copy(salary = Some(salaryAmount))).map {
          _ => Redirect(routes.QuickCalcController.showTaxCodeForm())
        }
      }
    )
  }

  def showAgeForm() = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        val form = aggregate.isOverStatePensionAge.map(OverStatePensionAge.form.fill).getOrElse(OverStatePensionAge.form)
        Ok(state_pension(form, aggregate.youHaveToldUsItems))
      case None =>
        Ok(state_pension(OverStatePensionAge.form, Nil))
    }
  }

  def submitAgeForm() = ActionWithSessionId.async { implicit request =>
    OverStatePensionAge.form.bindFromRequest.fold(
      formWithErrors => cache.fetchAndGetEntry.map {
        case Some(aggregate) => BadRequest(state_pension(formWithErrors, aggregate.youHaveToldUsItems))
        case None => BadRequest(state_pension(formWithErrors, Nil))
      },
      userAge => cache.fetchAndGetEntry.flatMap {
        case Some(aggregate) =>
          val updatedAggregate = aggregate.copy(isOverStatePensionAge = Some(userAge))
          cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showScottishRateForm())}
        case None => cache.save(QuickCalcAggregateInput.newInstance.copy(isOverStatePensionAge = Some(userAge))).map {
          _ =>  Redirect(routes.QuickCalcController.showScottishRateForm())
        }
      }
    )
  }

  def showScottishRateForm() = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        val form = aggregate.scottishRate.map(ScottishRate.form.fill).getOrElse(ScottishRate.form)
        Ok(scottish_income_tax_rate(form, aggregate.youHaveToldUsItems))
      case None =>
        Ok(scottish_income_tax_rate(ScottishRate.form, Nil))
    }
  }

  def submitScottishRateForm() = ActionWithSessionId.async { implicit request =>
    OverStatePensionAge.form.bindFromRequest.fold(
      formWithErrors => cache.fetchAndGetEntry.map {
        case Some(aggregate) => BadRequest(state_pension(formWithErrors, aggregate.youHaveToldUsItems))
        case None => BadRequest(state_pension(formWithErrors, Nil))
      },
      userAge => cache.fetchAndGetEntry.flatMap {
        case Some(aggregate) =>
          val updatedAggregate = aggregate.copy()
          cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showTaxCodeForm())}
        case None => cache.save(QuickCalcAggregateInput.newInstance.copy()).map {
          _ =>  Redirect(routes.QuickCalcController.showTaxCodeForm())
        }
      }
    )
  }

  def showHoursAWeek(valueInPence: Int) = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        Ok(hours_a_week(valueInPence, Salary.salaryInHoursForm, aggregate.youHaveToldUsItems))
      case None =>
        Ok(hours_a_week(valueInPence, Salary.salaryInHoursForm, Nil))
    }
  }

  def submitHoursAWeek(valueInPence: Int) = ActionWithSessionId.async { implicit request =>
    Salary.salaryInHoursForm.bindFromRequest.fold(
      formWithErrors => cache.fetchAndGetEntry.map {
        case Some(aggregate) => BadRequest(hours_a_week(valueInPence, formWithErrors, aggregate.youHaveToldUsItems))
        case None => BadRequest(hours_a_week(valueInPence, formWithErrors, Nil))
      },
      hours => cache.fetchAndGetEntry.flatMap {
        case Some(aggregate) =>
          val updatedAggregate = aggregate.copy(salary = Some(Salary(valueInPence, "hourly")))
          cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showTaxCodeForm())}
        case None => cache.save(QuickCalcAggregateInput.newInstance.copy(salary = Some(Salary(valueInPence, "hourly")))).map {
          _ =>  Redirect(routes.QuickCalcController.showTaxCodeForm())
        }
      }
    )
  }

  def showDaysAWeek(valueInPence: Int) = ActionWithSessionId.async { implicit request =>
    cache.fetchAndGetEntry.map {
      case Some(aggregate) =>
        Ok(days_a_week(valueInPence, Salary.salaryInDaysForm, aggregate.youHaveToldUsItems))
      case None =>
        Ok(days_a_week(valueInPence, Salary.salaryInDaysForm, Nil))
    }
  }

  def submitDaysAWeek(valueInPence: Int) = ActionWithSessionId.async { implicit request =>
    Salary.salaryInDaysForm.bindFromRequest.fold(
      formWithErrors => cache.fetchAndGetEntry.map {
        case Some(aggregate) => BadRequest(days_a_week(valueInPence, formWithErrors, aggregate.youHaveToldUsItems))
        case None => BadRequest(days_a_week(valueInPence, formWithErrors, Nil))
      },
      days => cache.fetchAndGetEntry.flatMap {
        case Some(aggregate) =>
          val updatedAggregate = aggregate.copy(salary = Some(Salary(valueInPence, "daily")))
          cache.save(updatedAggregate).map { _ => Redirect(routes.QuickCalcController.showTaxCodeForm())}
        case None => cache.save(QuickCalcAggregateInput.newInstance.copy(salary = Some(Salary(valueInPence, "daily")))).map {
          _ =>  Redirect(routes.QuickCalcController.showTaxCodeForm())
        }
      }
    )
  }

  private def nextPageOrSummaryIfAllQuestionsAnswered(aggregate: QuickCalcAggregateInput)
                                                     (next: Result)
                                                     (implicit request: Request[_]): Result = {
    if (aggregate.allQuestionsAnswered) {
      Redirect(routes.QuickCalcController.showScottishRateForm())
    } else {
      next
    }
  }

  private def redirectToNotYetDonePage(aggregate: QuickCalcAggregateInput): Result = {
    if (aggregate.taxCode.isEmpty) {
      Redirect(routes.QuickCalcController.showTaxCodeForm())
    } else if(aggregate.salary.isEmpty) {
      Redirect(routes.QuickCalcController.showSalaryForm())
    } else {
      Redirect(routes.QuickCalcController.showSalaryForm())
    }
  }
}
