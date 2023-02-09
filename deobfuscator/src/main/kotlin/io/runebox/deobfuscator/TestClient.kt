package io.runebox.deobfuscator

import java.applet.Applet
import java.applet.AppletContext
import java.applet.AppletStub
import java.awt.Color
import java.awt.Dimension
import java.awt.GridLayout
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import javax.swing.JFrame

class TestClient(private val file: File, private val vanillaFile: File) {

    companion object {

        private const val JAGEX_URL = "http://oldschool1.runescape.com/"

        @JvmStatic
        fun main(args: Array<String>) {
            if(args.size != 2) error("Args: <gamepack-jar> <vanilla-gamepack-jar>")

            val file = File(args[0])
            val vanillaFile = File(args[1])

            TestClient(file, vanillaFile).run()
        }
    }

    private val params = hashMapOf<String, String>()
    private val classLoader = URLClassLoader(arrayOf(file.toURI().toURL(), vanillaFile.toURI().toURL()), ClassLoader.getSystemClassLoader())
    private lateinit var applet: Applet

    fun run() {
        /*
         * Fetch jav_config parameters.
         */
        this.fetchParams()

        /*
         * Setup client applet.
         */
        val main = params["initial_class"]!!.replace(".class", "").replace("c", "C")
        applet = classLoader.loadClass(main).getDeclaredConstructor().newInstance() as Applet
        applet.background = Color.BLACK
        applet.size = Dimension(params["applet_minwidth"]!!.toInt(), params["applet_minheight"]!!.toInt())
        applet.preferredSize = applet.size
        applet.layout = null
        applet.setStub(applet.createStub())
        applet.init()

        /*
         * Create Window JFrame to hold the applet.
         */
        val clientRevision = params["25"]!!.toInt()
        val frame = JFrame("Test Client - Revision: $clientRevision")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = GridLayout(1, 0)
        frame.add(applet)
        frame.setLocationRelativeTo(null)
        frame.pack()
        frame.minimumSize = frame.size
        frame.isVisible = true
    }

    private fun fetchParams() {
        val lines = URL(JAGEX_URL + "jav_config.ws").readText().split("\n")
        lines.forEach {
            var line = it
            if(line.startsWith("param=")) {
                line = line.substring(6)
            }
            val idx = line.indexOf('=')
            if(idx >= 0) {
                params[line.substring(0, idx)] = line.substring(idx + 1)
            }
        }
    }

    private fun Applet.createStub() = object : AppletStub {
        override fun getCodeBase(): URL = URL(params["codebase"])
        override fun getDocumentBase(): URL = URL(params["codebase"])
        override fun getAppletContext(): AppletContext? = null
        override fun isActive(): Boolean = true
        override fun getParameter(name: String): String? = params[name]
        override fun appletResize(width: Int, height: Int) { applet.size = Dimension(width, height) }
    }
}