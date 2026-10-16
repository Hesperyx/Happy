package cn.hxy.happy.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hxy.happy.ui.core.theme.AccentBlue
import cn.hxy.happy.ui.core.theme.AccentGreen
import cn.hxy.happy.ui.core.theme.AccentRed
import cn.hxy.happy.ui.core.theme.HappyTheme

enum class ActionButtonStyle { Primary, Success, Danger }

@Composable
fun ActionButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: ActionButtonStyle = ActionButtonStyle.Primary
) {
    val textColor = when (style) {
        ActionButtonStyle.Primary -> AccentBlue
        ActionButtonStyle.Success -> AccentGreen
        ActionButtonStyle.Danger -> AccentRed
    }

    HappyCard(modifier = modifier, animateContentSize = false, onClick = onClick) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(12.dp, 6.dp)
        )
    }
}

@Composable
fun ConfigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HappyTheme.colors
    HappyCard(modifier = modifier, animateContentSize = false, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val colors = HappyTheme.colors
    // danger 只改变强调色：破坏性确认（删除、覆盖）需要一个红色的确定键，
    // 但按钮的形状、尺寸、字号跟普通主按钮保持一致。
    val bgColor = when {
        danger -> AccentRed
        isPrimary -> AccentBlue
        else -> colors.cardBackground
    }
    val textColor = if (isPrimary || danger) Color.White else colors.textPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(24.dp, 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

