import { Layers, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCategories, registerCategory, renameCategory } from '../../api/categories';
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
  // registered or renamed here, on top of what was loaded
  const [changed, setChanged] = useState<CategoryData[]>([]);
  const [registering, setRegistering] = useState(false);
  const [renaming, setRenaming] = useState<CategoryData>();
  const rows = [...new Map([...(categories.data ?? []), ...changed].map(row => [row.id, row])).values()].sort(byName);
  // the reloaded list already has the changes made meanwhile
  const retry = () => {
    setChanged([]);
    categories.retry();
  };
  const columns: TableColumn<CategoryData>[] = [
    { key: 'name', header: t('fields.name') },
    {
      key: 'actions', header: <span className="sm-visually-hidden">{t('ui.actions')}</span>, align: 'right',
      render: row => (
        <Button size="sm" variant="ghost" icon={Pencil} aria-label={t('catalogue.renameCategoryOf', { name: row.name })}
          onClick={() => setRenaming(row)}>
          {t('catalogue.rename')}
        </Button>
      ),
    },
  ];

  async function register(name: string) {
    const category = await registerCategory({ name });
    setChanged(list => [...list, category]);
    setRegistering(false);
    toast.success(t('catalogue.categoryRegistered', { name: category.name }));
  }

  async function rename(name: string) {
    if (!renaming) return;
    const category = await renameCategory(renaming.id, { name });
    setChanged(list => [...list, category]);
    setRenaming(undefined);
    toast.success(t('catalogue.categoryRenamed', { name: category.name }));
  }

  return (
    <div className="role-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.categories')}</h1>
        <p className="sm-caption">{t('catalogue.categoriesIntro')}</p>
        <Button icon={Plus} className="catalogue-page__create" onClick={() => setRegistering(true)}>{t('catalogue.newCategory')}</Button>
      </header>
      {categories.loading || categories.error !== undefined ? (
        <DataLoadState error={categories.error} retry={retry} />
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
      {/* a new dialog per category, so the field starts from its current name */}
      <CategoryDialog key={renaming?.id} open={renaming !== undefined} name={renaming?.name} title="catalogue.renameCategory"
        submitLabel="common.save" onClose={() => setRenaming(undefined)} onSubmit={rename} />
    </div>
  );
}
