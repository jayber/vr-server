package controllers

import play.api.Logger
import play.api.mvc.{Action, Controller}


class CommentController extends Controller {

  def post(spaceId: String, userId: String) = Action { request =>
    val body = request.body.asFormUrlEncoded.get
    Logger("client").info(s"userId: $userId, email: ${body("email").head}\n${body("message").head}")
    Ok
  }
}
