package controllers

import play.api.Logger
import play.api.mvc.{Action, Controller}


class ErrorController extends Controller {

  def post(message: String, stack: String) = Action {
    Logger.debug("reporting client error")
    Logger("clientError").error(s"message: $message\n$stack")
    Ok
  }
}
