import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import './ComponentPreview.css';
import type { StudyRoom } from './studymatch-ui';
import { AchievementGrid,Button,Card,LoginForm,Logo,PersonalityTest,StudyRoomCard,TextField,useToast } from './studymatch-ui';
import { Achievement } from './types/enums';

export function ComponentPreview() {
  const { t, i18n } = useTranslation(); const [name, setName] = useState(''); const toast = useToast();
  const room: StudyRoom = { id:'demo', subject:'StudyMatch', topic:t('rooms.title'),start:'2026-10-20T10:00:00',end:'2026-10-20T11:00:00',
    tutor:{id:'demo-tutor',name:'Demo'},capacity:8,participants:[],status:'SCHEDULED' };
  return <main className="component-preview">
    <header className="component-preview__header"><Logo /><a href="https://studymatch-ui.vercel.app/">{t('demo.docs')}</a>
      <label>{t('common.language')} <select aria-label={t('common.language')} value={i18n.resolvedLanguage}
        onChange={event => void i18n.changeLanguage(event.target.value)}><option value="en">English</option><option value="pt">Português</option></select></label>
    </header>
    <Card><h1>{t('demo.title')}</h1><p>{t('demo.intro')}</p>
      <TextField label={t('fields.name')} value={name} onChange={event => setName(event.target.value)} />
      <Button disabled={!name.trim()} onClick={() => toast.success(t('demo.hello',{name}))}>{t('demo.notify')}</Button>
    </Card>
    <section className="component-preview__grid">
      <Card title={t('demo.validation')}><LoginForm onSubmit={async () => {
        throw {code:'VALIDATION_FAILED',fieldErrors:[{field:'email',code:'EMAIL'},{field:'password',code:'SIZE',params:{min:8,max:20}}]};
      }} /></Card>
      <StudyRoomCard room={room} canJoin joinBlockedCode="STUDYROOM_FULL" onJoin={() => toast.info(t('demo.intro'))} />
    </section>
    <PersonalityTest questions={[{id:'demo-question',text:t('personality.title')}]} onSubmit={async () => {toast.success(t('common.submit'));}} />
    <AchievementGrid achievements={Achievement.map(id => ({id}))} />
  </main>;
}
