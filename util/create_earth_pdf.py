import os
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER

def crear_pdf():
    # 1. Configuración del documento (Tamaño carta y márgenes)
    nombre_archivo = "El_Planeta_Tierra_APA.pdf"
    doc = SimpleDocTemplate(
        nombre_archivo,
        pagesize=letter,
        rightMargin=72,  # 1 pulgada (Margen APA estándar)
        leftMargin=72,
        topMargin=72,
        bottomMargin=72
    )

    # 2. Definición de estilos de texto con codificación limpia
    styles = getSampleStyleSheet()

    titulo_style = ParagraphStyle(
        'TituloEstilo',
        parent=styles['Heading1'],
        fontSize=24,
        leading=28,
        alignment=TA_CENTER,
        spaceAfter=20,
        textColor="#1a1a1a"
    )

    subtitulo_style = ParagraphStyle(
        'SubtituloEstilo',
        parent=styles['Heading2'],
        fontSize=16,
        leading=20,
        spaceBefore=15,
        spaceAfter=10,
        textColor="#2c3e50"
    )

    cuerpo_style = ParagraphStyle(
        'CuerpoEstilo',
        parent=styles['BodyText'],
        fontSize=11,
        leading=16,
        alignment=TA_JUSTIFY,
        spaceAfter=12
    )

    referencia_style = ParagraphStyle(
        'ReferenciaEstilo',
        parent=styles['BodyText'],
        fontSize=10,
        leading=14,
        leftIndent=36,       # Sangría francesa obligatoria en APA
        firstLineIndent=-36, # Contrarresta la primera línea para simular la sangría
        spaceAfter=10
    )

    nota_style = ParagraphStyle(
        'NotaEstilo',
        parent=styles['BodyText'],
        fontSize=9,
        leading=13,
        textColor="#555555"
    )

    # 3. Construcción del contenido del PDF
    historia = []

    # Título Principal
    historia.append(Paragraph("El Planeta Tierra", titulo_style))
    historia.append(Spacer(1, 10))

    # Contenido Científico
    texto_1 = (
        "La Tierra es el tercer planeta desde el Sol en el Sistema Solar y se caracteriza "
        "por ser el más denso, así como el quinto mayor de los ocho planetas que lo componen. "
        "Dentro de la clasificación astronómica, destaca notablemente por ser \"el mayor de "
        "los cuatro planetas terrestres o rocosos\" (\"Tierra,\" 2026)."
    )
    historia.append(Paragraph(texto_1, cuerpo_style))

    texto_2 = (
        "Su posición e interacciones en el espacio exterior son fundamentales para la "
        "estabilidad planetaria. Además de albergar a la humanidad, su dinámica física y "
        "geológica interactúa estrechamente con otros objetos del espacio, especialmente con "
        "el Sol y la Luna, que es su único satélite natural."
    )
    historia.append(Paragraph(texto_2, cuerpo_style))
    historia.append(Spacer(1, 20))

    # Sección de Referencias
    historia.append(Paragraph("Referencias", subtitulo_style))

    cita_apa = (
        "Tierra. (2026, 18 de julio). En <i>Wikipedia</i>. "
        "<font color='#0066cc'>https://wikipedia.org</font>"
    )
    historia.append(Paragraph(cita_apa, referencia_style))
    historia.append(Spacer(1, 15))

    # Nota de formato
    texto_nota = (
        "<b>Nota de formato APA 7:</b> Al citar Wikipedia en APA, se utiliza el título del "
        "artículo en la posición del autor porque es una obra de autoría colectiva. Además, las "
        "normas recomiendan incluir la fecha exacta de la última revisión consultada y, si es "
        "posible, enlazar directamente a la versión archivada del historial del artículo para "
        "que el lector acceda exactamente a la misma información."
    )
    historia.append(Paragraph(texto_nota, nota_style))

    # 4. Compilación e impresión del archivo final
    doc.build(historia)
    print(f"¡Éxito! El archivo '{nombre_archivo}' ha sido generado en tu carpeta actual.")

if __name__ == "__main__":
    crear_pdf()
