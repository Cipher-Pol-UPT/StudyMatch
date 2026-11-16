import { Layers, Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCategories, registerCategory } from '../../api/categories';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Button, CategoryDialog, EmptyState, Table, useToast, type TableColumn } from '../../studymatch-ui';
import type { CategoryData } from '../../types/categories';
import '../../styles/catalogue.css';

const byName = (a: CategoryData, b: CategoryData) => a.name.localeCompare(b.name);

export function StaffCategoriesPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const categories = useRemoteData('categories', getCategories);
  const [registered, setRegistered] = useState<CategoryData[]>([]);
  const [registering, setRegistering] = useState(false);
  const rows = [...(categories.data ?? []), ...registered].sort(byName);
  const columns: TableColumn<CategoryData>[] = [{ key: 'name', header: t('fields.name') }];

  async function register(name: string) {
    const category = await registerCategory({ name });
    setRegistered(list => [...list, category]);
    setRegistering(false);
    toast.success(t('catalogue.categoryRegistered', { name: category.name }));
  }

  return (
    <div className="role-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.categories')}</h1>
        <p className="sm-caption">{t('catalogue.categoriesIntro')}</p>
        <Button icon={Plus} className="catalogue-page__create" onClick={() => setRegistering(true)}>{t('catalogue.newCategory')}</Button>
      </header>
      {categories.loading || categories.error !== undefined ? (
        <DataLoadState error={categories.error} retry={categories.retry} />
      ) : (
        <Table
          label={t('workspace.sections.categories')}
          columns={columns}
          rows={rows}
          rowKey={row => row.id}
          empty={<EmptyState icon={Layers} title={t('catalogue.noCategories')} body={t('catalogue.noCategoriesBody')} />}
        />
      )}
      <CategoryDialog open={registering} title="catalogue.newCategory" submitLabel="catalogue.registerCategory"
        onClose={() => setRegistering(false)} onSubmit={register} />
    </div>
  );
}
