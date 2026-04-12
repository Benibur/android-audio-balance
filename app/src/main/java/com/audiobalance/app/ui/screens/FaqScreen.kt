package com.audiobalance.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audiobalance.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.faq_screen_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.faq_back_description)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FaqItem(
                    question = stringResource(R.string.faq_q1_question),
                    answer = stringResource(R.string.faq_q1_answer)
                )
            }
            item {
                FaqItem(
                    question = stringResource(R.string.faq_q2_question),
                    answer = stringResource(R.string.faq_q2_answer)
                )
            }
            item {
                FaqItem(
                    question = stringResource(R.string.faq_q3_question),
                    answer = stringResource(R.string.faq_q3_answer)
                )
            }
            item {
                FaqItem(
                    question = stringResource(R.string.faq_q4_question),
                    answer = stringResource(R.string.faq_q4_answer)
                )
            }
            item {
                FaqItem(
                    question = stringResource(R.string.faq_q5_question),
                    answer = stringResource(R.string.faq_q5_answer)
                )
            }
            item {
                FaqItem(
                    question = stringResource(R.string.faq_q6_question),
                    answer = stringResource(R.string.faq_q6_answer)
                )
            }
            item {
                FooterSection()
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (expanded) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.faq_collapse_description else R.string.faq_expand_description
                    )
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FooterSection() {
    val context = LocalContext.current
    val url = stringResource(R.string.faq_github_url)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInBrowser,
                contentDescription = stringResource(R.string.faq_footer_link_description),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.faq_footer_link_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}
