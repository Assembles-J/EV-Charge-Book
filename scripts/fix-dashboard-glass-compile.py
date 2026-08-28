from pathlib import Path

path = Path('android/app/src/main/java/com/evchargebook/ui/dashboard/HeroVehicleCard.kt')
text = path.read_text(encoding='utf-8')

old_import = 'import androidx.compose.foundation.layout.matchParentSize\n'
if text.count(old_import) != 1:
    raise SystemExit('expected matchParentSize import exactly once')
text = text.replace(old_import, '', 1)

old_padding = '.padding(horizontal = 12.dp, bottom = 12.dp)'
new_padding = '.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)'
if text.count(old_padding) != 1:
    raise SystemExit('expected Hero overlay padding exactly once')
text = text.replace(old_padding, new_padding, 1)

old_match = '.matchParentSize()'
if text.count(old_match) != 1:
    raise SystemExit('expected matchParentSize call exactly once')
text = text.replace(old_match, '.fillMaxSize()', 1)

path.write_text(text, encoding='utf-8')
print('Applied Compose-compatible dashboard glass fixes')
