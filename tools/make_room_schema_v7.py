import json
from pathlib import Path

root = Path('/home/ubuntu/youneko-rate-fix/app/schemas/com.youneko.rate.data.local.YounekoDatabase')
source = json.loads((root / '8.json').read_text())
source['database']['version'] = 7
for entity in source['database']['entities']:
    if entity.get('tableName') != 'audio_analysis':
        continue
    entity['createSql'] = entity['createSql'].replace(', `spectrumJson` TEXT NOT NULL', '')
    entity['fields'] = [field for field in entity['fields'] if field.get('columnName') != 'spectrumJson']
(root / '7.json').write_text(json.dumps(source, ensure_ascii=False, indent=2) + '\n')
