package controllers

import scala.concurrent.duration._
import play.api._
import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json.Json
import models._
import dal._

import scala.concurrent.{ ExecutionContext, Future, Await }

import javax.inject._
import it.innove.play.pdf.PdfGenerator
import be.objectify.deadbolt.scala.DeadboltActions
import security.MyDeadboltHandler
import play.api.data.format.Formats._

class MeasureController @Inject() (deadbolt: DeadboltActions, repo:MeasureRepository, val messagesApi: MessagesApi)(implicit ec: ExecutionContext) extends Controller with I18nSupport {

  val newForm: Form[CreateMeasureForm] = Form {
    mapping(
      "name" -> nonEmptyText,
      "quantity" -> of[Double],
      "description" -> text,
      "measureId" -> longNumber)(CreateMeasureForm.apply)(CreateMeasureForm.unapply)
  }

  var measures = getMeasureMap()

  def getMeasureMap(): Map[String, String] = {
    Await.result(repo.getListNames().map {
      case (res1) =>
        val cache = collection.mutable.Map[String, String]()
        res1.foreach {
          case (key: Long, value: String) =>
            cache put (key.toString(), value)
        }
        cache put ("0", "Ninguno")
        cache.toMap
    }, 3000.millis)
  }

  def addGet = deadbolt.WithAuthRequest()()  { implicit request =>
    measures = getMeasureMap()
    Future { Ok(views.html.measure_add(new MyDeadboltHandler, newForm, measures)(request, messagesApi.preferred(request))) }
  }

  def index = deadbolt.WithAuthRequest()() { implicit request =>
    repo.list().map { res =>
      Ok(views.html.measure_index(new MyDeadboltHandler, res)(request, messagesApi.preferred(request)))
    }
  }

  def add = deadbolt.WithAuthRequest()() { implicit request =>
    newForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(Ok(views.html.measure_add(new MyDeadboltHandler, errorForm, measures)(request, messagesApi.preferred(request))))
      },
      res => {
        repo.create(
          res.name, res.quantity, res.description,
          res.measureId, measures(res.measureId.toString),
          request.session.get("userId").get.toLong,
          request.session.get("userName").get.toString).map { resNew =>
            Redirect(routes.MeasureController.show(resNew.id))
          }
      })
  }

  def getMeasures = deadbolt.WithAuthRequest()() { request =>
    repo.list().map { res =>
      Ok(Json.toJson(res))
    }
  }

  def getMeasuresReport = deadbolt.WithAuthRequest()() { request =>
    repo.list().map { res =>
      Ok(Json.toJson(res))
    }
  }

  // update required
  val updateForm: Form[UpdateMeasureForm] = Form {
    mapping(
      "id" -> longNumber,
      "name" -> nonEmptyText,
      "quantity" -> of[Double],
      "description" -> text,
      "measureId" -> longNumber)(UpdateMeasureForm.apply)(UpdateMeasureForm.unapply)
  }

  // to copy
  def show(id: Long) = deadbolt.WithAuthRequest()() { implicit request =>
    repo.getById(id).map { res =>
      Ok(views.html.measure_show(new MyDeadboltHandler, res(0))(request, messagesApi.preferred(request)))
    }
  }

  var updatedRow: Measure = _

  // update required
  def getUpdate(id: Long) = deadbolt.WithAuthRequest()() { implicit request =>
    measures = getMeasureMap()
    repo.getById(id).map { res =>
      updatedRow = res(0)
      val anyData = Map(
        "id" -> id.toString().toString(),
        "name" -> updatedRow.name,
        "quantity" -> updatedRow.quantity.toString(),
        "description" -> updatedRow.description.toString(),
        "measureId" -> updatedRow.measureId.toString())
      Ok(views.html.measure_update(new MyDeadboltHandler, updatedRow, updateForm.bind(anyData), measures)(request, messagesApi.preferred(request)))
    }
  }

  // delete required
  def delete(id: Long) = deadbolt.WithAuthRequest()() { implicit request =>
    repo.delete(id).map { res =>
      Redirect(routes.MeasureController.index)
    }
  }

  // to copy
  def getById(id: Long) = deadbolt.WithAuthRequest()() { request =>
    repo.getById(id).map { res =>
      Ok(Json.toJson(res))
    }
  }

  // update required
  def updatePost = deadbolt.WithAuthRequest()() { implicit request =>
    updateForm.bindFromRequest.fold(
      errorForm => {
        Future.successful(Ok(views.html.measure_update(new MyDeadboltHandler, updatedRow, errorForm, measures)(request, messagesApi.preferred(request))))
      },
      res => {
        repo.update(
          res.id, res.name, res.quantity, res.description,
          res.measureId, measures(res.measureId.toString),
          request.session.get("userId").get.toLong,
          request.session.get("userName").get.toString).map { _ =>
            Redirect(routes.MeasureController.show(res.id))
          }
      })
  }

}

case class CreateMeasureForm(name: String, quantity: Double, description: String, measureId: Long)

// Update required
case class UpdateMeasureForm(id: Long, name: String, quantity: Double, description: String, measureId: Long)
