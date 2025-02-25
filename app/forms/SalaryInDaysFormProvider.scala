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

package forms

import forms.mappings.CustomFormatters
import javax.inject.Inject
import models.Days
import play.api.data.Form
import play.api.data.Forms.{mapping, of}
import play.api.i18n.Messages
import play.api.data.format.Formats._

class SalaryInDaysFormProvider @Inject()() {

  def apply(): Form[Days] = Form(
    mapping(
      "amount"       -> of[BigDecimal],
      "how-many-a-week" -> of(CustomFormatters.dayValidation)
    )(Days.apply)(Days.unapply)
  )
}
