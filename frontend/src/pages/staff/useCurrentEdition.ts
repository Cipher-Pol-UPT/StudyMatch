import { useState } from 'react';
import { getCurrentEditions } from '../../api/enrolments';
import { useRemoteData } from '../../hooks/useRemoteData';

export type CurrentEdition = ReturnType<typeof useCurrentEdition>;

// this year's editions, with the first edition and its first class picked until Staff chooses
export function useCurrentEdition() {
  const editions = useRemoteData('current-editions', getCurrentEditions);
  const [editionId, setEditionId] = useState('');
  const [classSection, setClassSection] = useState('');
  const edition = editions.data?.find(item => String(item.id) === editionId) ?? editions.data?.[0];
  const section = edition?.classSections.includes(classSection) ? classSection : edition?.classSections[0] ?? '';
  return { editions, edition, section, setEditionId, setClassSection };
}
