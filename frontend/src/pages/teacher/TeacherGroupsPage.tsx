import { Check, RefreshCw, Sparkles, Trash2, Users } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useOutletContext, useParams } from 'react-router-dom';
import { isBackendError } from '../../api/errors';
import { getGradeSheet } from '../../api/grades';
import { discardGroups, generateGroups, publishGroups } from '../../api/groups';
import { GroupCard } from '../../components/groups/GroupCard';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Dialog, EmptyState, ProgressBar, useToast } from '../../studymatch-ui';
import type { GroupPreviewData, GroupStatus } from '../../types/groups';
import type { SubjectEditionData } from '../../types/studentDirectory';
import './assignments.css';

type Busy = 'generating' | 'publishing' | 'discarding';

// generate groups: the draft only lives on this page until viewing groups (#58) can load it back
export function TeacherGroupsPage() {
  const edition = useOutletContext<SubjectEditionData>();
  const { assignmentId } = useParams();
  const id = Number(assignmentId);
  const { t } = useTranslation();
  const toast = useToast();
  // the assignment's name and whether it's group work come from the grade sheet, like the Assignments tab
  const load = useCallback(async () => {
    const assignment = (await getGradeSheet(edition.id)).assignments.find(item => item.id === id);
    if (!assignment?.groupWork) throw { code: 'NOT_FOUND' };
    return assignment;
  }, [edition.id, id]);
  const assignment = useRemoteData(`groups:${edition.id}:${id}`, load);
  const [groups, setGroups] = useState<GroupPreviewData[]>();
  const [status, setStatus] = useState<GroupStatus>('DRAFT');
  const [busy, setBusy] = useState<Busy>();
  const [error, setError] = useState<string>();
  const [replacing, setReplacing] = useState(false);

  const message = (caught: unknown) => isBackendError(caught) ? t(`errors.${caught.code}`, caught.params ?? {}) : t('common.unexpectedError');

  async function generate(replacePublished = false) {
    setReplacing(false);
    setBusy('generating');
    setError(undefined);
    try {
      setGroups(await generateGroups(edition.id, id, replacePublished));
      setStatus('DRAFT');
    } catch (caught) {
      // published groups: warn first, students have seen them
      if (isBackendError(caught) && caught.code === 'GROUPS_ALREADY_PUBLISHED') setReplacing(true);
      else setError(message(caught));
    } finally {
      setBusy(undefined);
    }
  }

  async function act(action: Exclude<Busy, 'generating'>) {
    setBusy(action);
    setError(undefined);
    try {
      if (action === 'publishing') {
        await publishGroups(edition.id, id);
        setStatus('PUBLISHED');
        toast.success(t('groups.published'));
      } else {
        await discardGroups(edition.id, id);
        setGroups(undefined);
        toast.success(t('groups.discarded'));
      }
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(undefined);
    }
  }

  if (assignment.loading || assignment.error !== undefined || !assignment.data) {
    return <DataLoadState error={assignment.error} retry={assignment.retry} />;
  }

  const name = assignment.data.name;
  const draft = groups && status === 'DRAFT';
  const students = groups?.reduce((sum, group) => sum + group.members.length, 0) ?? 0;

  return (
    <>
      <div className="assignments__head">
        <div>
          <h2 className="sm-title">{t('groups.title', { name })}</h2>
          <p className="sm-caption">
            {!groups ? t('groups.intro') : draft ? t('groups.draftIntro') : t('groups.count', { count: groups.length, students })}
          </p>
        </div>
        {groups && (
          <div className="groups__actions">
            <Button variant="secondary" icon={RefreshCw} disabled={busy !== undefined} onClick={() => generate()}>{t('groups.regenerate')}</Button>
            {draft && (
              <>
                <Button variant="secondary" icon={Trash2} disabled={busy !== undefined} loading={busy === 'discarding'}
                  onClick={() => act('discarding')}>{t('groups.discard')}</Button>
                <Button icon={Check} disabled={busy !== undefined} loading={busy === 'publishing'}
                  onClick={() => act('publishing')}>{t('groups.publish')}</Button>
              </>
            )}
          </div>
        )}
      </div>
      {busy === 'generating' && <ProgressBar indeterminate label={t('groups.generating')} />}
      {error && <Banner tone="danger">{error}</Banner>}
      {!groups ? (
        <EmptyState icon={Users} title={t('groups.generate')} body={t('groups.generateBody')}
          action={<Button icon={Sparkles} disabled={busy !== undefined} onClick={() => generate()}>{t('groups.generate')}</Button>} />
      ) : (
        <div className="groups__list">
          {groups.map(group => (
            <GroupCard key={group.number} number={group.number} members={group.members} status={status}
              warning={group.sharedLanguages.length === 0 ? t('groups.noSharedLanguage') : undefined}
              explanation={group.sharedLanguages.length > 0
                ? t('groups.sharedLanguages', { languages: group.sharedLanguages.map(code => t(`languages.${code}`)).join(', ') })
                : undefined} />
          ))}
        </div>
      )}
      <Dialog open={replacing} onClose={() => setReplacing(false)} title={t('groups.replaceTitle')}
        description={t('groups.replaceDescription', { name })}
        footer={(
          <>
            <Button variant="secondary" onClick={() => setReplacing(false)}>{t('groups.keep')}</Button>
            <Button variant="danger" icon={RefreshCw} onClick={() => generate(true)}>{t('groups.replace')}</Button>
          </>
        )}>
        <Banner tone="warning">{t('groups.replaceWarning')}</Banner>
      </Dialog>
    </>
  );
}
