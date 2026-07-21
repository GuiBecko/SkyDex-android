package com.example.skydex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Registers(modifier: Modifier = Modifier){
	LazyColumn(
		modifier = modifier
			.fillMaxSize() // width: 100%, height: 100%
			.background(Color(0xFFF3F4F6)) // Cor de fundo leve (cinza claro)
			.padding(16.dp), // padding: 16px
		verticalArrangement = Arrangement.spacedBy(24.dp)
	) {

		item {
			Text(
				text = "Meus Registros",
				fontSize = 28.sp,
				fontWeight = FontWeight.Bold,
				color = Color.Black
			)
		}

		val registrosMock = listOf(
			Registro("Nuvem Nimbus", R.drawable.cumulonimbo, "Hoje, 14:30"),
			Registro("Eclipse", R.drawable.eclipse, "Ontem, 18:15"),
			Registro("Chuva de Meteoros", R.drawable.meteorosjpeg, "18/07/2026")
		)
		
		items(registrosMock) {registro ->
			RegistroCard(registro)
			Spacer(modifier = Modifier.height(8.dp))
		}
	}
}

@Composable
fun RegistroCard(registro: Registro){
	Card(
	) {
		Column(
			modifier = Modifier
				.padding(16.dp)

		) {
			Image(
				painter = painterResource(id = registro.picture),
				contentDescription = registro.title, // Bom para acessibilidade
				modifier = Modifier
					.fillMaxWidth()
					.height(150.dp),
				alignment = Alignment.CenterStart,
			)

			Text(text = registro.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
			Text(text = registro.description, color = Color.Gray, fontSize = 14.sp)
		}
	}
}

data class Registro(var title: String, var picture: Int, var description: String){

}
@Composable
fun RegistersPreview(){
	Scaffold(
		bottomBar = {
			FooterSection(
				aoClicarNearEvents = {},
				aoClicarHome = {},
				aoClicarMyRegistros = {},
				abaAtual = "meus registros"
			)
		}
			) { innerPadding ->
				Registers(modifier = Modifier.padding(innerPadding))
			}
}

