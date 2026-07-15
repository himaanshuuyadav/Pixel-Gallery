import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
fun Test() {
    val zoomState = rememberZoomState()
    Modifier.zoomable(zoomState, onTap = { })
}
