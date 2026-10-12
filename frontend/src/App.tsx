import { useState } from 'react'
import { Button, Card, Logo, StatCard, TextField, useToast } from '@studymatch/ui'
import './App.css'

function App() {
  const [name, setName] = useState('')
  const toast = useToast()

  return (
    <main className="component-preview">
      <header className="component-preview__header">
        <Logo />
        <a href="https://studymatch-ui.vercel.app/">Component documentation</a>
      </header>
      <Card as="section" tone="lavender" padding="lg">
        <h1>StudyMatch UI</h1>
        <p>The shared components for the StudyMatch frontend are ready to use.</p>
      </Card>
      <section className="component-preview__grid" aria-label="Component examples">
        <Card title="Try the components" description="An interactive preview of fields, buttons and notifications.">
          <form className="component-preview__form" onSubmit={(event) => {
            event.preventDefault()
            toast.success(`Hello, ${name.trim()}!`)
          }}>
            <TextField label="Your name" value={name} onChange={(event) => setName(event.target.value)} autoComplete="given-name" required />
            <Button type="submit" disabled={!name.trim()}>Show notification</Button>
          </form>
        </Card>
        <StatCard label="Library version" value="1.0.0" caption="Components, design tokens and application blocks" tone="mint" />
      </section>
    </main>
  )
}

export default App
