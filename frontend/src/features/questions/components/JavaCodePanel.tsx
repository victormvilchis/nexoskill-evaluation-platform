import { useMemo } from 'react'
import { Icon } from '../../../shared/components/Icon'

interface JavaCodeEditorProps {
  value: string
  onChange: (value: string) => void
}

function lineCount(value: string) {
  return Math.max(1, value.split('\n').length)
}

export function JavaCodeEditor({ value, onChange }: JavaCodeEditorProps) {
  const lines = useMemo(() => Array.from({ length: lineCount(value) }, (_, index) => index + 1), [value])

  return (
    <div className="java-ide java-ide-editor">
      <header className="java-ide-toolbar">
        <div className="java-ide-window-controls" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <div className="java-ide-tab">
          <Icon name="code" size={14} />
          <span>Main.java</span>
        </div>
        <span className="java-ide-language">Java</span>
      </header>
      <div className="java-ide-body">
        <div className="java-ide-gutter" aria-hidden="true">
          {lines.map((line) => <span key={line}>{line}</span>)}
        </div>
        <textarea
          aria-label="Código Java"
          autoCapitalize="off"
          autoCorrect="off"
          className="java-ide-textarea"
          placeholder={'public class Main {\n    public static void main(String[] args) {\n        // Escribe o pega el código Java\n    }\n}'}
          rows={Math.max(8, lines.length)}
          spellCheck={false}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== 'Tab') return
            event.preventDefault()
            const target = event.currentTarget
            const start = target.selectionStart
            const end = target.selectionEnd
            const next = `${value.slice(0, start)}    ${value.slice(end)}`
            onChange(next)
            requestAnimationFrame(() => {
              target.selectionStart = start + 4
              target.selectionEnd = start + 4
            })
          }}
        />
      </div>
    </div>
  )
}

export function JavaCodePreview({ code }: { code: string }) {
  const lines = code.split('\n')
  return (
    <div className="java-ide java-ide-preview">
      <header className="java-ide-toolbar">
        <div className="java-ide-window-controls" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <div className="java-ide-tab">
          <Icon name="code" size={14} />
          <span>Main.java</span>
        </div>
        <span className="java-ide-language">Java</span>
      </header>
      <div className="java-ide-body">
        <div className="java-ide-gutter" aria-hidden="true">
          {lines.map((_, index) => <span key={index}>{index + 1}</span>)}
        </div>
        <pre className="java-ide-code"><code>{code}</code></pre>
      </div>
    </div>
  )
}
