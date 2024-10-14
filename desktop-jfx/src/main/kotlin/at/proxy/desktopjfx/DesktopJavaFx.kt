package at.proxy.desktopjfx

import javafx.application.Application
import javafx.stage.Stage

class DesktopJavaFx : Application() {

    override fun start(stage: Stage) {
        stage.title = "JavaFX Hello World"
        stage.show()
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(DesktopJavaFx::class.java)
        }
    }
}