package controllers


import com.amazonaws.regions._
import com.amazonaws.services.simpleemail._
import com.amazonaws.services.simpleemail.model._
import play.api.Logger
import play.api.mvc.{Action, Controller}


class CommentController extends Controller {


  def post(spaceId: String, userId: String) = Action { request =>
    val body = request.body.asFormUrlEncoded.get
    Logger("client").info(s"userId: $userId, email: ${body("email").head}\n${body("message").head}")
    sendEmail(userId, body)
    Ok
  }

  private def sendEmail(userId: String, body: Map[String, Seq[String]]) = {

    val sender = System.getProperty("SENDER")
    val recipients = System.getProperty("RECIPIENTS").split("""[\s*\;\,]""")
    Logger.debug(s"recipients: ${recipients.mkString("| ")}")

    val destination: Destination = new Destination().withToAddresses(recipients: _*)

    val message = new Message().withSubject(new Content().withData("BeatLab feedback")).
      withBody(new Body().withText(new Content().
        withData(s"User - $userId\nEmail address -  ${body("email").head}\n\nFeedback Message -\n\n${body("message").head}")))

    val request = new SendEmailRequest().withSource(sender).withDestination(destination).withMessage(message)

    AmazonSimpleEmailServiceClientBuilder.standard().withRegion(Regions.EU_WEST_1).build().sendEmail(request)


  }
}
