package dev.goij.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import java.util.EnumSet

class GoBreakpointProperties : XBreakpointProperties<GoBreakpointProperties>() {
    override fun getState() = this

    override fun loadState(state: GoBreakpointProperties) {}
}

class GoLineBreakpointType :
    XLineBreakpointType<GoBreakpointProperties>("standalone-go-line", "Go Line Breakpoints") {
    override fun canPutAt(file: VirtualFile, line: Int, project: Project) =
        !file.isDirectory && file.extension == "go"

    override fun createBreakpointProperties(file: VirtualFile, line: Int) = GoBreakpointProperties()

    override fun getVisibleStandardPanels(): EnumSet<StandardPanels> =
        EnumSet.noneOf(StandardPanels::class.java)
}
