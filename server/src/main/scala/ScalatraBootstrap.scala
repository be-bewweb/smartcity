import javax.servlet.ServletContext

import _root_.akka.actor.ActorSystem
import be.info.unamur.api.ZonesEndpoint
import be.info.unamur.persistence.DatabaseUtils
import be.info.unamur.{ActorsServlet, MainServlet}
import org.scalatra._

import scala.language.postfixOps

/** Bootstraps the Scalatra application.
  *
  * @author Noé Picard
  */
class ScalatraBootstrap extends LifeCycle with DatabaseUtils {

  // Initialize the Actor system here to do it just once and pass it to the servlet that need it
  val system = ActorSystem("SmartCity")

  override def init(context: ServletContext) {
    configureDatabase()

    // Mount servlets
    context mount(new MainServlet, "/*")
    context mount(new ActorsServlet(system), "/actors/*")
    context mount(new ZonesEndpoint, "/api/zones/*")
  }

  override def destroy(context: ServletContext) {
    system terminate

    closeDatabase()
  }
}

