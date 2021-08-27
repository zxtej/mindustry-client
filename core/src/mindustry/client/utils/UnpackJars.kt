package mindustry.client.utils

import java.io.File
import java.io.InputStream

class UnpackJars {
    fun unpack() {
        val jars = arrayOf("bcprov-jdk15on.jar", "bcpkix-jdk15on.jar", "bctls-jdk15on.jar")
        val outputDir = File(this::class.java.protectionDomain.codeSource.location.toURI().path).parentFile

        for (fi in jars) {
            println("Unloading $fi")
            val output = outputDir.resolve(fi)
            if (!output.exists()) {
                val inp: InputStream = this::class.java.getResourceAsStream("/$fi")!!
                output.writeBytes(inp.readBytes())
                inp.close()
            }
        }
    }
}
