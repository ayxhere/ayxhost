package com.imayx.host

import android.content.Context
import android.net.Uri
import java.io.*
import java.util.zip.ZipInputStream

object ZipProjectManager {
    fun importZip(context:Context, uri:Uri, projectName:String):File {
        val safe=projectName.replace(Regex("[^A-Za-z0-9._-]"),"_").ifBlank{"site"}
        val root=File(context.filesDir,"projects/$safe")
        if(root.exists()) root.deleteRecursively()
        root.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input){"Cannot open ZIP"}
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var e=zis.nextEntry
                while(e!=null) {
                    val out=File(root,e.name)
                    val canonical=out.canonicalPath
                    require(canonical.startsWith(root.canonicalPath+File.separator)){"Unsafe ZIP path"}
                    if(e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    e=zis.nextEntry
                }
            }
        }
        return root
    }
}
