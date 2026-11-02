# IG-L Erkennung

Android-App, die auf österreichischen Autobahnen per Handykamera (oder angesteckter USB-Webcam)
erkennt, ob ein Tempolimit nach dem **Immissionsschutzgesetz-Luft (IG-L)** gilt – erkennbar am
Zusatz **„IG-L“** unter dem Tempolimit-Schild bzw. auf der mittleren Anzeige der
Schilderbrücken (Verkehrsbeeinflussungsanlage). Für Fahrzeuge mit grünem Kennzeichen gilt ein
IG-L-Limit nicht; die App zeigt dann groß an, dass das höhere Tempo (standardmäßig 130 km/h)
erlaubt ist.

```
┌──────────────────────────────┬─────────────────────┐
│                              │     IG-L AKTIV      │
│   Kamerabild                 │       (100)         │
│   (optional mit Erkennungs-  │       [IG-L]        │
│    rahmen)                   │ E-Auto: bis 130 km/h│
│   Zoom ───●──────            │ [Zurücksetzen] [⚙]  │
└──────────────────────────────┴─────────────────────┘
```

## Funktionen

- Erkennt „IG-L“ auf **LED-Anzeigen** von Schilderbrücken und auf **Zusatztafeln** unter Blechschildern
- Liest das zugehörige **Tempolimit** (30–130) und prüft es über den **roten Ring** (keine
  Fehlalarme durch Telefonnummern auf LKW o. Ä.)
- **Zeitliche Glättung**: ein einzelner Lesefehler löst nichts aus; der Status wird erst
  aufgehoben, wenn ein gut sichtbares Schild *ohne* IG-L passiert wurde
- **Sprachausgabe** bei Statuswechsel („IG-L aktiv. Tempo 100 gilt nicht für Elektroautos.“)
- **Bild-im-Bild**: läuft als kleines Fenster neben der Navigations-App weiter
- Rückkamera, Frontkamera oder **externe USB-Webcam** wählbar, Zoom-Regler
- Alles läuft **offline auf dem Gerät** (ML Kit Texterkennung, Modell im APK); keine Bilder
  werden gespeichert oder übertragen
- Debug-Anzeige mit erkannten Texten, Schildern und Suchbereich

## So funktioniert die Erkennung

```
Kamerabild (CameraX, 1080p, bis zu ~8 Bilder/s – je nach Gerät)
  → aufrecht drehen, oberen Suchbereich ausschneiden (Standard: obere 65 %)
  → Texterkennung (ML Kit, on-device)
  → IglTextMatcher        „IG-L“, „IGL“, „1G-L“ … (OCR-Verwechslungen toleriert, bewertet)
  → SpeedLimitMatcher     „100“, „1OO“ … nur gültige Limits
  → RedRingVerifier       Strahlen vom Zahlenmittelpunkt: roter Ring in gleichem Abstand?
  → FrameAnalyzer         Ergebnis je Bild (IG-L neben einem Schild zählt stärker)
  → IglStateTracker       Ergebnis über die Zeit → Status: unbekannt / IG-L aktiv / kein IG-L
```

Regeln des `IglStateTracker`:

| Ereignis | Wirkung |
|---|---|
| „IG-L“ in ≥ 2 Bildern innerhalb von 3 s (Score-Summe ≥ 1,8) | **IG-L aktiv** (mit erkanntem Limit) |
| Tempolimit-Schild passiert, gut lesbar (Zahl ≥ 20 px hoch, obere Bildhälfte), kein IG-L-Hinweis | **kein IG-L** |
| Schild nur klein/weit unten gesehen oder unsicherer IG-L-Hinweis | Status bleibt unverändert |
| 15 min (einstellbar) ohne Bestätigung | wieder **unbekannt** |

## Projektstruktur

```
core/   Erkennungslogik in reinem Kotlin (ohne Android) + Unit-Tests
app/    Android-App: CameraX, ML Kit, Jetpack Compose
```

`core` ist als eigener Gradle-Build eingebunden (`includeBuild`), damit die Logik auch ohne
Android SDK gebaut und getestet werden kann. Die Tests verwenden synthetische Schilderbilder
(LED-Anzeige mit Punktring, Blechschild, rote LKW-Plane, oranger Brückenträger …).

## Bauen & Installieren

**Fertige APK:** Unter **Releases** liegt die APK zum direkten Download aufs Handy. Dort die
Datei `igl-erkennung-….apk` öffnen und beim ersten Mal die Installation aus dieser Quelle
erlauben. Außerdem baut jeder Push eine Debug-APK (Actions → Lauf „Build“ → Artefakt
`igl-erkennung-debug-apk`, als ZIP).

Neues Release erstellen: Tag `vX.Y.Z` pushen oder den Workflow „Build“ manuell mit
`release_tag` starten – die APK wird dann automatisch angehängt.

**Android Studio:** Projekt öffnen, Gerät anschließen, ▶ ausführen.

**Kommandozeile** (JDK 17+ und Android SDK nötig):

```bash
./gradlew :app:installDebug
```

Nur die Erkennungslogik testen (kein Android SDK nötig):

```bash
./gradlew -p core test
```

Voraussetzungen: Android 8.0 (API 26) oder neuer.

## Benutzung im Auto

1. Handy **quer** in eine Halterung an Windschutzscheibe/Armaturenbrett, Kamera nach vorne.
   Der Horizont sollte etwa in der Bildmitte liegen.
2. Einmalig die **Debug-Anzeige** einschalten (Einstellungen) und prüfen, dass Schilder
   oberhalb der gelben Suchbereich-Linie liegen.
3. **Zoom** ca. 2× einstellen: Schilder werden früher lesbar und bleiben trotzdem lange im Bild.
4. Für die Navigation einfach die App verlassen – sie läuft als Bild-im-Bild-Fenster weiter.

Bitte die App nicht während der Fahrt bedienen.

### Externe USB-Webcam

Android stellt USB-Webcams (UVC) auf vielen Geräten ab Android 14 als normale Kamera bereit.
Dann erscheint sie in den Einstellungen als „Externe Kamera (USB)“ und wird bei „Automatisch“
bevorzugt. Die Webcam vor dem App-Start anstecken (bzw. die App danach neu starten). Unterstützt
das Handy keine externen Kameras, taucht sie nicht auf – dafür wäre eine eigene UVC-Anbindung
(z. B. über libuvc) nötig, siehe unten.

## Grenzen und nächste Schritte

- Die Erkennung basiert auf Texterkennung. Bei großer Entfernung, Gegenlicht, Nacht, Regen oder
  flimmernden LED-Anzeigen kann „IG-L“ übersehen werden. Ein echter Praxistest auf
  A1/A2/A12/A13 ist der wichtigste nächste Schritt (Debug-Anzeige einschalten).
- Verbesserungsideen:
  - eigenes Objekterkennungsmodell (z. B. YOLO/TFLite) für Schilder und IG-L-Anzeigen, trainiert
    mit eigenen Aufnahmen – robuster und auf größere Entfernung
  - Abgleich mit GPS/Kartendaten (IG-L-Abschnitte sind bekannt), um Fehlerkennungen außerhalb
    dieser Abschnitte auszuschließen
  - eigene UVC-Anbindung für Handys ohne Android-Unterstützung für USB-Webcams
  - Hintergrundbetrieb als Vordergrunddienst, Android-Auto-Anzeige

## Rechtlicher Hinweis

Die App ist eine Hilfe und kann Schilder übersehen oder falsch lesen. Maßgeblich sind immer die
tatsächliche Beschilderung und die geltende Rechtslage. Die Ausnahme vom IG-L-Tempolimit gilt
nur für Fahrzeuge mit grünem Kennzeichen (reiner Elektroantrieb), nicht für Hybride.
